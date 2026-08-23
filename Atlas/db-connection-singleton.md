# DbConnection 单例优化 (2026-08-19)

> 一句话总结: 通过进程级单例 + Application 预热,把启动期 SQLite `getWritableDatabase()` 调用从 3 次压到 1 次,消除 `openOrCreateDatabase` 重复 IO + `onCreate` / `onUpgrade` 重复触发的隐患。

## 版本锚点

- **Commit**: `db18a1b`(`perf(startup): 启动期 SQLite getWritableDatabase 从 3 次降到 1 次`)
- **Tag**: `v1.5-dbperf`(annotated)
- **优化前基线**: `9080432`(`feat(drawer): reorder nav_subject above nav_home in side menu`)
- **diff 范围**: 14 个文件, +556 / -124
  - 新增: `Atlas/db-connection-singleton.md`、`Atlas/INDEX.md`、`Atlas/subject-fragment-load-flow.md`、`App.java`、`DbConnection.java`
  - 修改: `AndroidManifest.xml`、`MainActivity.java`、`WebViewActivity.java`、`HomeFragment.java`、`SubjectFragment.java`、`TagsFragment.java`、`LinkDao.java`、`LinkDbHelper.java`、`SubjectDao.java`

## 背景

排查"打开 → 跳主题页慢"的过程中,逐步定位到 SQLite 连接被反复打开的问题。本文档记录完整发现过程和原理,供后续类似性能问题参考。

## 关键文件

- `app/src/main/java/person/notfresh/readingshare/db/DbConnection.java` — 单例门面,DCL + Application 生命周期
- `app/src/main/java/person/notfresh/readingshare/App.java` — `Application.onCreate` 预热入口
- `app/src/main/AndroidManifest.xml:17` — `android:name=".App"` 注册
- `app/src/main/java/person/notfresh/readingshare/db/LinkDao.java` — 新构造 `(SQLiteDatabase)`,旧构造 deprecated,9 处 `dbHelper.getXxxDatabase()` 已统一改成注入的 `database` 字段
- `app/src/main/java/person/notfresh/readingshare/db/SubjectDao.java` — 同上,删除了隐式持有 LinkDao
- `app/src/main/java/person/notfresh/readingshare/MainActivity.java:95` — 启动路径首个接入点
- `app/src/main/java/person/notfresh/readingshare/ui/subject/SubjectFragment.java:59` — 主题 Fragment 接入点
- `app/src/main/java/person/notfresh/readingshare/ui/home/HomeFragment.java:343` — 首页接入点
- `app/src/main/java/person/notfresh/readingshare/ui/tag/TagsFragment.java:247` — 标签页接入点
- `app/src/main/java/person/notfresh/readingshare/WebViewActivity.java:666/756/1102` — 三处后台线程 DAO 接入点
- `app/src/main/java/person/notfresh/readingshare/db/LinkDbHelper.java:11` — `DEFAULT_DATABASE_NAME` 改为 public,供单例引用

## 发现过程

### 第一步:确认主诉与现场

用户报告"打开应用,跳到主题页很慢"。观察默认启动路径:
- `MainActivity.onCreate` 中 `linkDao.open()` → 打开 SQLite
- 默认 tab=0 → 先 `navigate(nav_home)` → HomeFragment 完整生命周期 → 再点底部菜单切到 `nav_subject`

启动期 SQLite 连接获取:**3 次**
| # | 位置 | 备注 |
|---|---|---|
| 1 | `MainActivity.java:96 linkDao.open()` | Activity 启动 |
| 2 | `HomeFragment.java:343 linkDao.open()` | Home 进栈 |
| 3 | `SubjectFragment.java:60 subjectDao.open()` | Subject 进栈,且 `SubjectDao.open()` 内部还会顺带打开它持有的 LinkDao,**实际又触发一次** |

### 第二步:确认 SubjectFragment 自身

单独看 `SubjectFragment`:
- `onCreate` 只创建 `SubjectEntryManager`(SharedPreferences 包装),无 IO
- `onCreateView` 打开两个 DAO + 挂 Adapter + `setupDragAndDrop`
- `onResume` 才是数据加载入口

发现 `setupDragAndDrop()` 默认就挂 `ItemTouchHelper`,但 `subject == null` 时 `recyclerView` 是 GONE,这套机制白挂。`linkDao` 也无条件打开,但只在点链接跳转时才会被用到。

### 第三步:看 MainActivity 拖累项

- `linkDao.open()` 在 `MainActivity.onCreate` 就执行(行 95-96),但 MainActivity 自己并不直接查链接
- `handleNavigation` 默认走 `nav_home`,意味着启动永远先 inflate HomeFragment 再切到 Subject
- `checkClipboardPermission()` 在 `onCreate` 调用 `checkClipboard()`,会延迟 500ms 弹"保存链接"对话框打断用户
- `default_tab` 默认值是 0(首页)

### 第四刀:跳过 + 注释(最小改动)

- `MainActivity.java:273` 默认 tab 改 2(主题页)
- `MainActivity.java:145` 注释剪贴板监听

**用户反馈**:启动到主题页明显变快。

### 第五步:深入 SQLite 连接模型

定位到核心问题:**多个 DAO 各自 `new LinkDbHelper(context)` + `getWritableDatabase()`,重复触发 native `openOrCreateDatabase`**。

`getWritableDatabase()` 真实代价(伪代码视角):

```
getWritableDatabase() {
    synchronized(this) {                     // ① 线程锁
        if (mDatabase != null && mDatabase.isOpen())
            return mDatabase;                // ② 缓存命中,直接返回
        return getDatabaseLocked();          // ③ 真正打开
    }
}

getDatabaseLocked() {
    db = mContext.openOrCreateDatabase(...);  // native sqlite3_open_v2,5-15ms
    currentVersion = db.getVersion();         // page 1 读,0.1-0.5ms
    if (currentVersion != mVersion) {
        if (currentVersion == 0) onCreate(db);    // 建表,5-30ms
        else if (currentVersion< mVersion) onUpgrade(db, ...);  // 迁移
        db.setVersion(mVersion);
    }
    db.execSQL("PRAGMA foreign_keys = ON;");
}
```

**关键事实**:
- `new LinkDbHelper(context)` **不打开 SQLite**(只是赋 4 个字段,纳秒级)
- 只有 `getWritableDatabase()` 才真正打开文件、跑版本检查、可能触发 `onCreate` / `onUpgrade`
- `SQLiteOpenHelper` 内部有 `mDatabase` 缓存,**同一个实例第二次调用会直接返回**;但不同实例每次都走完整路径

启动期 3 次 `getWritableDatabase()` 的代价构成:
| 次数 | 文件状态 | 实际开销 |
|---|---|---|
| 第 1 次 | 文件不存在或低版本 | onCreate 建 11 张表 → 10-50ms |
| 第 2 次 | 文件已存在,版本对得上 | sqlite3_open_v2 + 版本检查 → 5-20ms |
| 第 3 次 | 同上 | 同上 |

**真正贵的不是 `new LinkDbHelper`,而是每次的 native open + 版本检查**。

### 第六步:设计 DbConnection 单例

需求对齐(已确认):
- **Application 级单例**,进程级生命周期
- **永不关闭**(连接由进程结束统一回收)
- **保证线程安全**(DCL + 写操作 ReentrantLock)
- **只改启动路径**,保留 DAO 旧构造为 deprecated

API 设计:
```java
public final class DbConnection {
    private static volatile DbConnection instance;

    public static DbConnection get(Context anyContext) {
        DbConnection local = instance;
        if (local != null) return local;
        synchronized (DbConnection.class) {
            if (instance == null) {
                Application app = (Application) anyContext.getApplicationContext();
                instance = new DbConnection(app);
            }
            return instance;
        }
    }

    public SQLiteDatabase writable() { return defaultDb; }
    public SQLiteDatabase readable() { return defaultDb; }
    public<T> T withWriteLock(Callable<T> task) throws Exception { ... }
    public LinkDbHelper helperFor(String databaseName) { ... }
}
```

预热: `App.onCreate` 里调一次 `DbConnection.get(this)`,**触发首次 `getWritableDatabase()`**,后续所有 DAO 拿到的都是现成连接引用。

### 第七步:DAO 改造

`LinkDao` / `SubjectDao` 新增 `(SQLiteDatabase)` 构造,旧构造标 deprecated,`open/close` 兼容空实现:

```java
public LinkDao(SQLiteDatabase database) { this.database = database; }

@Deprecated
public LinkDao(Context context) { dbHelper = new LinkDbHelper(context); }

public void open() {
    if (database == null && dbHelper != null) {
        database = dbHelper.getWritableDatabase();  // 旧构造兼容
    }
}
public void close() {
    if (dbHelper != null) dbHelper.close();          // 旧构造兼容
}
```

### 第八步:NPE 事故

**漏改了一处**:`LinkDao` 内部 7 处 `SQLiteDatabase db = dbHelper.getXxxDatabase()`,这些方法走的是 `dbHelper` 字段而不是注入的 `database` 字段。新构造下 `dbHelper == null`,`getTagsWithCount` 直接 NPE,首页数据加载失败。

修复:把 7 处全部改成 `SQLiteDatabase db = database;`:
- `deleteLink` / `togglePinStatus` / `updateClickCount` / `getPinnedLinks` / `deleteTag` / `deleteTagWithLinks` / `getTagsWithCount`

**教训**:结构性变更必须全文扫 `dbHelper.` / `helper.` 等所有 helper 字段引用,不能只看构造函数附近。

### 第九步:启动路径接入

五个调用点接入新构造:
- `MainActivity`(启动入口)
- `HomeFragment`
- `TagsFragment`
- `SubjectFragment`
- `WebViewActivity`(三处后台线程局部 DAO)

`onDestroy` / `onDestroyView` 里的 `linkDao.close()` / `subjectDao.close()` 全部删除 — **共享连接不能关**。

## 优化前后对比

| 维度 | 之前 | 之后 |
|---|---|---|
| 启动期 `getWritableDatabase()` 次数 | 3 | 1 |
| 启动期 `openOrCreateDatabase` native 调用 | 3 | 1 |
| `LinkDbHelper` 实例数(进程内) | 3-4 个 | 1 个 |
| 启动期 `onCreate` / `onUpgrade` 触发 | 风险 N 次 | 0 次(已建好的文件不会触发) |
| Fragment 视图销毁时是否关连接 | 是(每次 `close()`) | 否(共享连接) |
| 默认 tab | 0(首页) | 2(主题页) |
| 剪贴板监听 | onCreate 调,500ms 后弹对话框 | 注释掉,不再打断 |

## 原理总结

### 为什么 `new LinkDbHelper` 不贵但 `getWritableDatabase` 贵

- 构造 `SQLiteOpenHelper` 子类 = 4 个字段赋值,纳秒级
- `getWritableDatabase()` 触发:
  - native `sqlite3_open_v2`:5-15ms(每次)
  - `db.getVersion()`:0.1-0.5ms(每次)
  - 首次 `onCreate` 建表:5-30ms
  - 版本不匹配时 `onUpgrade`:数 ms 到数十 ms
- `SQLiteOpenHelper` 内部缓存 `mDatabase`,**同一实例第二次调命中,不同实例每次都走完整路径**

### 单例模式收益的本质

`DbConnection` 不是省了"new 几个对象"的成本,而是省了 `openOrCreateDatabase` native 调用的 IO + 版本检查。**单例让"打开数据库文件"这件 IO 密集型操作只在 App.onCreate 跑一次**。

### 为什么用 DCL 而不是静态初始化

```java
static {
    instance = new DbConnection(...);  // 静态初始化
}
```

不行,因为 `DbConnection` 需要 `Application` 上下文,而静态初始化器在类加载时跑,那时拿不到 Application(进程刚启动)。必须用 DCL 让首次访问延后到 `App.onCreate` 完成之后。

### 为什么共享连接不需要 close

- Android 进程结束时 OS 回收所有 native handle,SQLite 文件锁自动释放
- 关闭共享连接会导致其他正在使用的 DAO 拿到已关闭的 `SQLiteDatabase` 引用,立即抛 `IllegalStateException`
- 内存压力下杀进程 → 静态字段全部回收 → 没有任何 close 调用需要执行

## 坑

- `LinkDao.java` 全文扫不到位会出 NPE(已踩过,见"第八步")。后续对 DAO 做结构性变更,**必须全文 grep `dbHelper\.` / `helper\.` 等 helper 字段引用**
- `SubjectDao` 历史上隐式持有 `LinkDao`,在 `open()` 里顺带打开它。这个耦合让 SubjectFragment 启动期实际触发 2 次 `getWritableDatabase()`,改造时同步删除该内部 LinkDao
- `ArchiveFragment` / `LinksAdapter` 用了非默认库名 `archive_db`,不能强制走默认单例。通过 `DbConnection.helperFor("archive.db")` 按需创建 helper,不在单例内缓存
- `WebViewActivity` 三处后台线程 DAO 走共享连接后,SQLite 内部锁会串行化并发写。当前是只读查询为主,无影响;如果后续加重事务,**改用 `DbConnection.withWriteLock(...)` 包起来**
- `linkDao.close()` / `subjectDao.close()` 全部不能保留**(包括 `onDestroy` 路径),否则共享连接会被错误关闭,后续 DAO 立即崩溃

## 后续可做(暂未动)

- 剩余约 20 处 `new LinkDao(context)` / `new SubjectDao(context)` 调用点(对话框、Adapter、Manager、Util) 仍走旧构造,`open/close` 是空操作,行为正确但每次仍走 `instance` null 检查。后续可全部迁移到 `(SQLiteDatabase)` 构造,代码更干净
- `HomeFragment` 报告里指出的 "路径 A + 路径 B 重复加载"问题,本轮未涉及 — 见 `Atlas/2026-08-13-homefragment-startup-bottleneck.md`
- `getLinksGroupByDate` 仍是 `getAllLinks` 全表 + Java 端分组循环,主线程开销大。可改成单条 SQL + `strftime` 分桶

## 关联

- 相关: HomeFragment 启动瓶颈分析(`Atlas/2026-08-13-homefragment-startup-bottleneck.md`)
- 相关: SubjectFragment 加载流程(`Atlas/subject-fragment-load-flow.md`)
- 待探索: SQLiteOpenHelper 内部缓存机制 + 多线程并发事务下的锁策略