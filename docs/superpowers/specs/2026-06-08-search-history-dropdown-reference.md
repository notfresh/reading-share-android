# 搜索历史下拉功能参考（2026-06-08）

> 用于把"搜索框下拉历史 + pin + maxCount 配置"功能迁移到其它项目。语言无关，伪代码为主。
> 当前实现位于 duxiang-ext 浏览器扩展的 popup 顶部搜索框。

## 一、概述

搜索框获得焦点且输入为空时，弹出一个下拉列表，展示用户历史搜索词。每条历史带 📌 固定 / ✕ 删除两个操作。键入时自动记录到历史，受 maxCount 上限的 LRU 淘汰约束，但 pinned 条目永远不被淘汰。下拉交互、存储、淘汰策略三者解耦，可独立迁移。

## 二、核心概念（4 个）

1. **历史是"持久化的搜索词集合"**，不是"搜索结果缓存"。每条只有 `text / pinned / lastUsed` 三个字段。
2. **pinned 与 unpinned 是两类对象**，互不干扰淘汰和排序。
3. **内存缓存 + 显式刷新**：UI 不直读存储，每次写操作（add / togglePin / delete / setMaxCount）后必须 `refreshHistoryCache()`，否则下拉显示陈旧。
4. **程序化赋值不触发"输入变化"事件**：从下拉点选填回输入框时，必须手动同步依赖该事件的所有副作用（如清空按钮可见性、自动记录历史等）。

## 三、功能行为清单

| # | 触发 | 行为 |
|---|------|------|
| 1 | 搜索框获得焦点 且 input 为空 | 弹出下拉 |
| 2 | 搜索框获得焦点 且 input 有内容 | 不弹（用户正在输入，不打扰） |
| 3 | 键入 | 防抖后：执行搜索 + 记录到历史 + 刷新缓存 |
| 4 | 点击下拉中的历史词文本 | 填入 input + 隐藏下拉 + 同步清空按钮 + 跑搜索 + 更新 lastUsed |
| 5 | 点击 📌 | 翻转 pin 状态 + 刷新缓存 |
| 6 | 点击 ✕ | 从历史中删除 + 刷新缓存 |
| 7 | 点击 × 清空按钮 | 清空 input + 隐藏 × + 立即（无防抖）跑搜索 + 显示下拉 |
| 8 | 搜索框失焦 | 延迟 200ms 后隐藏下拉（给点击下拉项留时间窗） |
| 9 | 下拉被 mousedown | 阻止默认行为（避免 input 先失焦） |
| 10 | 设置页改 maxCount | 改上限 + 立即按 LRU 淘汰多余 + 刷新缓存 |

## 四、数据模型

存储用 KV 形式（key-value 数据库、本地文件、IndexedDB、localStorage 等任选）：

```
key: "searchHistory"
value: {
  items: [
    { text: "react",      pinned: true,  lastUsed: 1715760000000 },
    { text: "tag:前端",   pinned: false, lastUsed: 1715759000000 }
  ],
  maxCount: 10
}
```

**排序规则**：pinned 在前、非 pinned 在后；组内都按 `lastUsed` 降序。

**字段含义**：
- `text`：原始搜索词（trim 后存，大小写敏感）
- `pinned`：是否固定（true = 永不淘汰）
- `lastUsed`：上次使用时间戳（毫秒），用于 LRU 排序
- `maxCount`：非 pinned 上限，默认 10

## 五、模块拆分

```
┌──────────────────────────────────────┐
│ 存储层 (Storage)                       │  ← 纯 CRUD + 淘汰，无 UI
├──────────────────────────────────────┤
│ 视图层 (View)                          │  ← 下拉渲染、事件绑定
├──────────────────────────────────────┤
│ 样式 (Style)                          │  ← 下拉定位、滚动条、hover
├──────────────────────────────────────┤
│ 宿主集成 (Host Integration)             │  ← 何时刷新、何时隐藏
└──────────────────────────────────────┘
```

**关键解耦**：存储层不知道 UI 长啥样，视图层不直接做淘汰。

## 六、实现关键逻辑（伪代码）

### 6.1 存储层 API（5 个）

```
loadHistory()                    -> List<Item>          # 读 + 排序
addSearchKeyword(keyword)        -> void                # 写：trim → upsert → 淘汰 → save
togglePinKeyword(keyword)        -> void                # 写：翻转 pinned → save
deleteKeyword(keyword)           -> void                # 写：过滤掉 → save
setMaxCount(count)               -> void                # 写：改上限 → 立即淘汰 → save
```

`Item` = `{ text: string, pinned: bool, lastUsed: int (ms) }`

### 6.2 排序

```
function sortItems(items):
    pinned   = items.filter(it == it.pinned).sort(by lastUsed desc)
    unpinned = items.filter(it == not it.pinned).sort(by lastUsed desc)
    return pinned + unpinned
```

### 6.3 LRU 淘汰（核心算法）

```
function addSearchKeyword(keyword):
    if keyword is empty or only whitespace: return
    data = load_from_storage()
    trimmed = keyword.trim()

    existing = find item in data.items where item.text == trimmed
    if existing exists:
        existing.lastUsed = current_time_ms()
    else:
        data.items.append({ text: trimmed, pinned: false, lastUsed: current_time_ms() })

    # 淘汰：只动非 pinned
    unpinned = data.items.filter(it == not it.pinned)
    if unpinned.length > data.maxCount:
        unpinned_sorted = sort unpinned by lastUsed desc
        to_remove = unpinned_sorted[ data.maxCount .. end ]   # 丢弃尾部
        data.items = filter out items whose text is in to_remove
                   (pinned 始终保留)

    save_to_storage(data)
```

**关键不变量**：pinned 条目不参与 `unpinned.length` 计算，所以 pinned 多了不会挤掉 unpinned 配额。

### 6.4 maxCount 收缩时的立即淘汰

```
function setMaxCount(count):
    data = load_from_storage()
    data.maxCount = count
    unpinned = data.items.filter(it == not it.pinned)
    if unpinned.length > count:
        unpinned_sorted = sort unpinned by lastUsed desc
        to_remove = unpinned_sorted[ count .. end ]
        data.items = filter out items whose text is in to_remove
    save_to_storage(data)
```

### 6.5 缓存失效模式（视图层）

```
state history_cache = []       # 内存中的有序列表

function refreshHistoryCache():
    history_cache = loadHistory()                       # 重读存储
    if dropdown.is_visible():
        renderSearchHistory()                            # 仅可见时重渲

# 每次写操作后必跟 refresh
togglePinKeyword(kw)
refreshHistoryCache()

deleteKeyword(kw)
refreshHistoryCache()

setMaxCount(n)
refreshHistoryCache()
```

**为什么不监听存储变更事件？**：单 UI 场景下显式调更可控；多 context 时再加 storage listener 也行，但通常过度设计。

### 6.6 下拉显示 / 隐藏

```
on search_input_focus:
    if search_input.value.trim() is empty:
        showSearchHistoryDropdown()         # 渲染并显示

on search_input_blur:
    schedule hideSearchHistoryDropdown() after 200ms    # 延迟，给点击下拉留时间

on dropdown mousedown:
    prevent default                                       # 阻止 input 先失焦

on dropdown click (event delegation):
    if click target matches "[data-action=...]":
        action = target.dataset.action
        keyword = target.dataset.keyword
        if action == "togglePin": togglePinKeyword(kw); refreshHistoryCache()
        if action == "delete":    deleteKeyword(kw);    refreshHistoryCache()
        return

    if click target matches ".search-history-item":
        keyword = target.dataset.keyword
        search_input.value = keyword                      # 程序化赋值
        dropdown.hide()
        syncClearButtonVisibility()                       # 手动同步副作用
        runSearch(keyword)                                # 同步或防抖都行
        addSearchKeyword(keyword)                         # 更新 lastUsed
        refreshHistoryCache()
```

**事件顺序关键**：mousedown(preventDefault) → mouseup → click(成功触发) → 异步处理。如果不阻止 mousedown，blur 会先于 click 触发，dropdown 提前隐藏，click 落空。

### 6.7 防抖搜索 + 历史记录

```
on search_input_value_change (debounced 300ms):
    text = search_input.value.trim()
    if clear_button exists:
        clear_button.visible = (text is not empty)
    filtered = searchLinks(text, all_data)
    handleSearchResults(filtered, text)              # 视图层回调
    if text is not empty:
        addSearchKeyword(text)
        refreshHistoryCache()
```

### 6.8 渲染结构（视图模板）

```
container: search-history-dropdown (absolute, top: 100%, z-index: 100)

  for each item in history_cache where item.pinned:
      row (data-keyword=item.text):
          text | [button pin, filled]  [button delete]

  if both pinned and unpinned exist:
      separator (1px line)

  for each item in history_cache where not item.pinned:
      row (data-keyword=item.text):
          text | [button pin, outline] [button delete]

  if history_cache is empty:
      row (disabled, gray text): "暂无搜索历史"
```

## 七、UI / 交互细节

- **下拉 trigger**：只有 focus + input 空才弹。focus 时有内容不弹（避免遮挡用户输入视野）。
- **空状态**：无历史时显示灰色"暂无搜索历史"，整行不可点。
- **键盘**：当前实现不处理 ↑↓/Enter 选词——`tag:/title:/url:` 前缀语法场景下不致命，可后续迭代。
- **i18n**：当前全中文文案，迁移时抽常量即可。
- **× 清空按钮**（位于 input 内）：仅在 input 有内容时显示（macOS 风）；与历史下拉无功能耦合。

## 八、集成点（迁移时怎么挂上去）

最小化接入只需 5 步：

1. **复制存储层**（5 个 API + sortItems + LRU 淘汰），换掉 KV 后端
2. **HTML 模板**：搜索框 + 紧邻的下拉容器，二者包在 `position: relative` 父容器里
3. **CSS 模板**：下拉绝对定位、scrollbar 样式、hover 反馈
4. **宿主逻辑**：
   - 维护 `history_cache` + `refreshHistoryCache()`
   - 绑定 focus / blur / mousedown / click 四类事件
   - 搜索执行处插入 `addSearchKeyword` + `refreshHistoryCache`
5. **设置入口**：暴露一个数字输入框 → 保存时 `setMaxCount` + `refreshHistoryCache`

## 九、坑 / 细节（迁移时容易踩的）

| 坑 | 后果 | 规避 |
|---|---|---|
| blur 同步隐藏下拉 | 点击下拉项 click 永远触发不了 | blur 用 200ms 延迟 |
| 忘了 mousedown preventDefault | 同上 | 下拉监听 mousedown 并 preventDefault |
| 程序化 `input.value = x` | 不触发"输入变化"事件 → × 按钮不显示 / 不自动入历史 | 点选填回后手动同步所有依赖该事件的副作用 |
| 写完存储忘了 refreshHistoryCache | 下拉显示陈旧 | 封装 `mutate(fn)` 包装函数：fn + refresh |
| setMaxCount 不淘汰 | 改小上限后旧数据还在 | 改上限时立即按 lastUsed 淘汰多余 |
| 淘汰条件用 `>=` | 多删一条 | 必须严格 `>` |
| 没 trim 后比较 | 大小写或前后空格导致重复条目 | 入库前 `.trim()`、比较用 `===`（不忽略大小写，由用户决定） |
| 历史词含 HTML / 模板特殊字符 | 注入 | 渲染时 escape（按目标语言/模板引擎的转义函数） |
| 同时有 pinned 和 unpinned 时的分隔线 | 没分隔看着乱 | pinned 长度 > 0 且 unpinned 长度 > 0 才显示 |

## 十、迁移清单（自检用）

- [ ] 存储后端可读写 `{items, maxCount}`
- [ ] 5 个 API 行为一致（特别是 LRU 淘汰边界）
- [ ] 下拉 trigger 逻辑：focus + 空 input
- [ ] 事件时序：mousedown preventDefault + blur 200ms 延迟
- [ ] click 委托分发（pin / delete / 选词）
- [ ] 每次写后 refresh cache
- [ ] 程序化赋值处手动同步副作用
- [ ] 设置页 maxCount 改动触发立即淘汰
- [ ] 渲染时 escape 用户输入
- [ ] pinned / unpinned 分组 + 可选分隔线
