# HomeFragment 启动瓶颈分析与重复加载问题 (2026-08-13)

## 背景

排查 `app/src/main/java/person/notfresh/readingshare/ui/home/HomeFragment.java` 中启动相关方法（`onCreate` / `onCreateOptionsMenu` / `onCreateView` / `onViewCreated`，行 175–453）的可加速点时，发现首页启动期存在**链接数据被加载两次**的问题，且两次都跑在主线程、每次都跑全表查询。

本次报告记录：
- 两条数据加载路径的具体位置与代价
- 为什么注释掉 `onViewCreated` 后"看起来还能加载"——但实际上是误打误撞
- 启动期可优化的全部热点（按收益排序）
- 推荐落地步骤与最小改动方案

---

## 一、两条数据加载路径

启动 `HomeFragment` 时，链接列表会被填入 adapter 两次。第一次在 `onViewCreated` 同步完成，第二次由 `loadTags` 异步线程跑完后回到主线程触发。

### 路径 A：`onViewCreated` 同步加载（行 585–662）

```java
@Override
public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    if (linkDao != null && adapter != null) {
        List<LinkItem> pinnedLinks = linkDao.getPinnedLinks();                       // 主线程 SQL
        Map<String, List<LinkItem>> groupedLinks = linkDao.getLinksGroupByDate();   // 主线程 SQL
        adapter.setPinnedLinks(pinnedLinks);
        adapter.setGroupedLinks(groupedLinks);
        recyclerView.post(() -> adapter.notifyDataSetChanged());                    // 多余
    }
}
```

- `getPinnedLinks()` 一次 `SQLiteDatabase.rawQuery`
- `getLinksGroupByDate()` 内部先调 `getAllLinks()` 全表拉回，再在内存里遍历每条 link 做日期分组（每条 `new SimpleDateFormat("yyyy-MM-dd")` + `new Date(timestamp)`）
- 末尾的 `recyclerView.post(() -> adapter.notifyDataSetChanged())` **完全冗余**：`setPinnedLinks` / `setGroupedLinks` 内部已经调用 `notifyDataSetChanged`（见 `refreshLinksList` 注释 `HomeFragment.java:1428` 自己也确认了）

### 路径 B：`loadTags` 异步回调（行 1770–2005）

`onCreateView` 末尾（行 435）调用 `loadTags()`：

```java
new Thread(() -> {
    Map<String, Integer> tagsWithCount = linkDao.getTagsWithCount();    // 后台线程
    int noTagCount = linkDao.getLinksWithoutTags().size();
    for (Map.Entry<String, Integer> entry : tagsWithCount.entrySet()) {
        long tagId = linkDao.getTagIdByName(entry.getKey());
        allTagItems.add(new TagsAdapter.TagItem(tagId, entry.getKey(), entry.getValue()));
    }
    activity.runOnUiThread(() -> {
        // ... 切分 fixedTags / collapsedTags、设置到 adapter ...
        if (adapter != null && linkDao != null) {
            if (selectedTagNames.isEmpty()) {
                refreshLinksList();              // ← 触发路径 B-1
            } else {
                updateContentBySelectedTags();   // ← 触发路径 B-2
            }
        }
    });
}).start();
```

#### 路径 B-1：`refreshLinksList`（行 1352）

```java
List<LinkItem> pinnedLinks = linkDao.getPinnedLinks();          // 主线程 SQL（第 3 次）
List<LinkItem> allLinks = linkDao.getAllLinks();                // 主线程 SQL（第 4 次）
// 内存里过滤出 normalLinks
Map<String, List<LinkItem>> groupedLinks = groupLinksByDate(normalLinks);  // Java 端再分一次组
adapter.setPinnedLinks(pinnedLinks);
adapter.setGroupedLinks(groupedLinks);
```

#### 路径 B-2：`updateContentBySelectedTags`（行 2015）

```java
List<LinkItem> pinnedLinks = linkDao.getPinnedLinks();                              // 主线程 SQL
Map<String, List<LinkItem>> groupedLinks = linkDao.getLinksGroupByDate();           // 主线程 SQL
adapter.setPinnedLinks(pinnedLinks);
adapter.setGroupedLinks(groupedLinks);
adapter.notifyDataSetChanged();
```

### 一次启动，主线程 DB 调用次数

| # | 调用 | 位置 |
|---|---|---|
| 1 | `getPinnedLinks()` | onViewCreated:593 |
| 2 | `getLinksGroupByDate()` → 内部 `getAllLinks()` | onViewCreated:594 |
| 3 | `getPinnedLinks()` | refreshLinksList:1366 或 updateContentBySelectedTags:2031 |
| 4 | `getAllLinks()` | refreshLinksList:1367（额外多一次置顶过滤） |
| 5 | `getLinksGroupByDate()` → 内部 `getAllLinks()` | updateContentBySelectedTags:2032 |

用户首屏看到的列表被设置 2 次（路径 A 先填一次，路径 B 异步回来再覆盖一次）。中间可能观察到"先空 → 填一次 → 再填一次"的闪烁，或滑动时列表"跳"一下。

---

## 二、为什么注释掉 `onViewCreated` 后"还能加载"

因为路径 B 还在跑。只要 `loadTags` 异步线程不早退（异常路径之一：`!isAdded()` / `linkDao == null` / `Activity == null` 等，行 1790/1802/1812/1824/1845/1851），回到主线程后必然走 `refreshLinksList()` 或 `updateContentBySelectedTags()`，重新填一次 adapter。

这意味着：

1. **不省时间**：路径 B 内部跑 `getPinnedLinks` + `getAllLinks` + `groupLinksByDate`，代价与路径 A 相当甚至更高（多了一次单独的 `getAllLinks()` 用于过滤置顶）
2. **不稳定**：若 `loadTags` 在某次启动中触发上面任意一个 early-return，列表会一直是空的——只是当前没踩到
3. **代码误导**：注释掉 `onViewCreated` 里的同步加载看起来是"优化"，但实际启动路径未变，仅是把"首屏可见"推迟到 `loadTags` 完成 + 路径 B 回主线程那一帧

---

## 三、启动期可优化热点（按收益排序）

### 3.1 主线程同步全表查询（最大瓶颈）

- `getPinnedLinks()`、`getLinksGroupByDate()`（内部 `getAllLinks()` + Java 端分组循环）都跑在主线程
- `getLinksGroupByDate` 循环里每次都 `new SimpleDateFormat`（线程局部未复用）+ `new Date(timestamp)`
- 链接表超过千行时这一段就是肉眼可感的卡顿（百毫秒级）

**推荐改造**：
- 合并成单条 SQL：`SELECT ... FROM links ORDER BY is_pinned DESC, timestamp DESC`
- 在 SQL 里按日期分桶：`strftime('%Y-%m-%d', timestamp, 'unixepoch', 'localtime')`
- 把整个查询丢到 `Executors.newSingleThreadExecutor()` 上跑，回主线程 `setData`
- adapter 用 DiffUtil 或 `notifyItemRangeInserted` 替代全量 `setGroupedLinks` + `notifyDataSetChanged`

### 3.2 `onCreateView` 里同步开 DB、写 SharedPreferences、构建两个 FlexboxLayoutManager

- `linkDao.open()`（行 342–344）同步打开 SQLite，首次 `getReadableDatabase()` 可能触发创建/升级。Application 启动期预热一次可消除此延迟
- `searchHistoryManager = new SearchHistoryManager(...)` + `refreshHistoryCache()`（行 375–376）：搜索历史用 SharedPreferences 实现，每条记录都读一遍 IO，**阻塞主线程**且对首页首屏并非必需——延迟到 `OnFocusChangeListener(hasFocus=true)` 第一次获焦时再读
- `initTagRecyclerView`（行 670–822）创建两个 `FlexboxLayoutManager` + 两个 `TagsAdapter` + `ItemTouchHelper`。首页默认 `tagsContainer` 折叠（GONE）时仍在初始化它们。Flexbox 第一次 `onMeasure` 比 LinearLayout 重。可延后到 `tagsContainer` 第一次 VISIBLE 时再初始化

### 3.3 `onViewCreated` 末尾多余的 `recyclerView.post(() -> notifyDataSetChanged())`

完全冗余。`setPinnedLinks` / `setGroupedLinks` 内部已经 `notifyDataSetChanged`（`HomeFragment.java:1428` 注释确认）。删除可省一帧。

### 3.4 `OnTouchListener` 内 `searchEditText.clearFocus()`

行 363–368。触摸 RecyclerView 默认就会让焦点离开 `EditText`，此 listener 既没意义又在每次 touch event 上多调一次 `clearFocus`。删除。

### 3.5 `onCreateOptionsMenu` 内的死代码与日志洪水

行 224–229：

```java
View actionView = requireActivity().findViewById(statisticsMenuItem.getItemId());
if (actionView != null) {
    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) actionView.getLayoutParams();
    params.rightMargin = getResources().getDimensionPixelSize(R.dimen.statistics_button_margin);
    actionView.setLayoutParams(params);
}
```

在 `onCreateOptionsMenu` 阶段菜单图标还没渲染，`findViewById` 大概率返回 null，整段 setMargin 是空操作。挪到菜单图标的 `OnAttachStateChangeListener` 或 `Activity.onCreateOptionsMenu` 之后再处理，或直接删除（建议放 XML `android:layout_marginEnd`）。

另外行 193–221 内的 10+ 条 `Log.d` 在生产路径上是纯开销，单次合计可能吃掉 1–2 帧。

### 3.6 `restoreShuffleModeState()` 在 `onCreate` 读 SharedPreferences（行 188）

可与 DB 一起挪到异步任务。单独看不重，但叠加在第 1、2 项里属于同一窗口期。

### 3.7 `loadTags` 异步线程里每个 tag 单独查 `getTagIdByName`（行 1828）

```java
for (Map.Entry<String, Integer> entry : tagsWithCount.entrySet()) {
    long tagId = linkDao.getTagIdByName(entry.getKey());  // 每条 tag 一次 SQL
    ...
}
```

N 个标签 = N 次 SQL。可改成 `WHERE name IN (?, ?, ...)` 单次查询。当前用户标签数量小，影响有限，但属于易改易收的优化点。

---

## 四、推荐落地步骤（最小改动 → 最大收益）

| 步骤 | 改动 | 预期收益 |
|---|---|---|
| A | 删除 `onViewCreated` 末尾的 `recyclerView.post(() -> adapter.notifyDataSetChanged())` | 一帧 |
| B | 把 `searchHistoryManager` 初始化与 `refreshHistoryCache()` 挪到首次获焦时 | 几十毫秒 |
| C | 单条 SQL 替代 `getPinnedLinks + getLinksGroupByDate`：SQLite `strftime` 分桶，`ORDER BY is_pinned DESC, timestamp DESC` | 几十~一百多毫秒 |
| D | `refreshLinksList` / `updateContentBySelectedTags` 内 DAO 调用改异步，回主线程 setData + DiffUtil | 首帧不再被 IO 阻塞 |
| E | `initTagRecyclerView` 延后到 `tagsContainer` 第一次 VISIBLE 时执行 | 几十毫秒（默认折叠场景完全跳过） |
| F | 删除 `onCreateOptionsMenu` 内 `findViewById` 改 margin 的死代码；移除生产日志 | 微小但免费 |

按 D + C + B + E 完成，首屏"链接列表"从"先等 DB IO + List 构建 + 全表 notifyDataSetChanged"变成"先出 UI，再异步填数据"——肉眼可感知的差异。

---

## 五、结论

- **启动期链接列表被加载 2 次**（路径 A 同步 + 路径 B 异步回调），主线程 DB 调用 3–5 次。两次都会触发 adapter 全量重绘
- 注释掉 `onViewCreated` 的同步加载是**误打误撞**：路径 B 仍跑同样代价的查询，且首次出现"先空再填"的闪烁，且在 `loadTags` 早退时会留下空列表的隐患
- 真正的优化是**只跑一次 + 异步**：单条 SQL 分桶 + 异步执行 + DiffUtil 增量更新
- 最小改动排序：A (删冗余 post) → B (搜索历史懒加载) → C (单 SQL) → D (异步化) → E (标签懒初始化) → F (清日志/死代码)