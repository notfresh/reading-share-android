# Bug: sync LWW 折叠丢字段(remark / click_count)

## 复现

1. 给 link 加 remark
2. 任务面板一键全部关闭(SIGKILL)
3. 重新打开 app
4. **remark 字段丢失**

## 根因(三个独立 bug,同一家族)

### 1. `LinkJson.fromJsonString` 漏调 `setRemark`

文件: `app/src/main/java/person/notfresh/readingshare/model/LinkJson.java:46-79`

```java
// 修复前
LinkItem item = new LinkItem(title, url, sourceApp, originalIntent, targetActivity);
item.setId(id);
item.setTimestamp(timestamp);
item.setPinned(isPinned);
item.setSummary(summary);
item.setClickCount(clickCount);  // 漏:remark 没 set
item.setTags(tags);
```

反序列化事件 payload 时 `item.remark` 留为 null。`LinkEventApplier.handleUpdate` → `LinkDao.replaceById` 走 `CONFLICT_REPLACE` 整行覆盖,把本地有 remark 的数据冲成空。

**修法**: 加 `item.setRemark(remark);` 一行。

### 2. `LinkDao.replaceById` 漏写 `click_count`

文件: `app/src/main/java/person/notfresh/readingshare/db/LinkDao.java:100-119`

```java
values.put(COLUMN_REMARK, item.getRemark());
values.put(COLUMN_SUMMARY, item.getSummary());
values.put("is_pinned", item.isPinned() ? 1 : 0);
// 漏:click_count 没 put
database.insertWithOnConflict(TABLE_LINKS, null, values, CONFLICT_REPLACE);
```

`CONFLICT_REPLACE` 走 `insertWithOnConflict`,ContentValues 没写的列会用**列默认值 0**。每次 sync pull 折叠都会把本地累积的 click_count 冲回 0。

**修法**: 加 `values.put("click_count", item.getClickCount());` 一行。

### 3. `PRAGMA synchronous = NORMAL` 在 SIGKILL 下丢数据(防御性)

文件: `app/src/main/java/person/notfresh/readingshare/db/LinkDbHelper.java:18, 223, 343-347`

`NORMAL` 模式下 commit 只 fsync WAL 文件,不全 fsync 主 db。任务面板一键全部关闭不触发 `onActivityStopped` 回调,checkpoint 也不跑,已 commit 的写入可能停留在 WAL,新进程读不到。

**修法**:
- `DATABASE_VERSION 17 → 18`
- `PRAGMA synchronous = NORMAL` → `FULL`(每 commit 多一次 fsync,代价是写入吞吐下降)
- 加 `onUpgrade` 18 块(只走一遍升级路径让 `onConfigure` 真正生效)
- `App.onCreate` 注册 `ActivityLifecycleCallbacks`,最后一个 Activity onStop 时调 `DbConnection.checkpoint()`(覆盖"上滑退出/返回键/系统内存杀"路径)

不覆盖:多任务面板"全部关闭"(SIGKILL)那一刻"已 commit 但未 checkpoint"的最近写入仍可能丢(毫秒级);其它退出路径全部安全。

## 调用链总览

```
用户点"确定"备注
  ↓
LinksAdapter.updateLinkRemark(item)                          [LinksAdapter.java:1170]
  ├─ linkDao.updateLinkRemark(id, remark)                    [LinkDao.java:1080]
  │   └─ database.update(TABLE_LINKS, ...)                  ← 写 links 表
  └─ EventLogClient.update("links", id, time, json)         ← 写 events 表 + scheduleAutoPush(1500ms 后推)
                                                              ↓
                                                              推到服务端
                                                              ↓
App 启动 → triggerStartupSync → EventLogClient.pull("links")
  ↓
linkApplier.apply(e)                                         [EventLogClient.java:349]
  ↓
LinkEventApplier.handleUpdate(e)                            [LinkEventApplier.java:70]
  ├─ decode(e) → LinkJson.fromJsonString(json)              ← Bug 1 在此(漏 setRemark)
  └─ linkDao.replaceById(item)                              ← Bug 2 在此(漏写 click_count)
      └─ insertWithOnConflict(..., CONFLICT_REPLACE)        整行覆盖
```

## 教训(给未来的自己)

1. **LWW + CONFLICT_REPLACE 整行覆盖的语义下,事件 payload 完整性 = 一切**。任何字段从 JSON 漏 set 都会让那条事件变成"字段空"的事件,污染整个折叠。
2. **`insertWithOnConflict` 的 ContentValues 缺字段 = 列默认值**。要把所有"该事件必须带"的字段都列上,不能依赖默认值。
3. **加新字段时的"双向同步 checklist"**:
   - `LinkItem` 加字段
   - `LinkJson.toJsonString` 写
   - `LinkJson.fromJsonString` 读 + set
   - `LinkDao.replaceById` ContentValues put
   - `LinkDao.createLinkItemFromCursor` cursor 读 + set
   - `LinkDao.cursorToLinkItem` cursor 读 + set(置顶路径)
   - 任何一处漏掉都会"特定场景丢字段"
4. **`PRAGMA synchronous` 是连接级配置,改了 `onConfigure` 不会对已存在的 db 生效**。必须升 `DATABASE_VERSION` 触发 `onUpgrade`,让旧 db 走一遍升级路径,新连接上才会用新 PRAGMA。
5. **诊断 log 优先打**"写完立刻同连接回读"和"冷启动后原始 SQL 直查"——能在 5 分钟内定位"丢在写路径还是读路径"。

## 经验:用 adb 真机诊断的具体动作

(保留给以后类似问题复用的 SOP)

1. 写完后立刻同连接 rawQuery 一次:
   ```java
   try (Cursor c = database.rawQuery("SELECT remark FROM links WHERE _id = ?", new String[]{String.valueOf(linkId)})) {
       if (c.moveToFirst()) Log.d("DIAG", "re-read remark=[" + c.getString(0) + "]");
   }
   ```
2. 重启后 HomeFragment.onCreateView 里直接 rawQuery 一次,看 db 落盘状态:
   ```java
   try (Cursor c = linkDao.getDatabase().rawQuery(
       "SELECT _id, remark, length(remark), hex(remark) FROM links WHERE _id IN (4117,4118,...)", null)) {
       while (c.moveToNext()) Log.d("DIAG", "rawSQL id=" + c.getLong(0) + " remark=[" + c.getString(1) + "]");
   }
   ```
3. 抓 logcat 加 tag 过滤: `adb logcat -s DIAG_Remark:* HomeFragment:* LinkDao:*`

## 提交

- Commit: `0311908 fix(db): 修复 sync 折叠丢失 remark/clickCount + WAL 落盘防御(2.3.15.9 → 2.3.16.3)`
- versionCode 35→39, versionName 2.3.15.9→2.3.16.3
- 7 files changed, 93 insertions(+), 13 deletions(-)
