### 实现方案：WebViewActivity 上下文翻页（仅首页列表，16 条上下文）

## 背景
当前应用在首页列表点击一条内容后，会进入 `WebViewActivity` 打开对应 URL。用户希望在 `WebViewActivity` 内直接查看“当前列表里的上一条/下一条内容”，无需返回首页列表。

项目现状（已确认）：
- `WebViewActivity` 目前主要通过 `Intent extra: "url"` 打开页面。
- `WebViewActivity` 已具备“在同一个 Activity 内切换 URL”的能力（支持 `onNewIntent()`，也可以直接 `loadUrl`）。
- 首页列表点击打开 WebView 的入口集中在适配器侧（例如 `LinksAdapter.openLink(...)`）。

## 目标（Goals）
- **仅在首页列表入口实现**：从首页列表进入 `WebViewActivity` 后，支持“上一条 / 下一条”按钮。
- **翻页顺序严格等于用户当时看到的首页列表顺序**（包含筛选结果、排序、分组展开等对顺序的影响）。
- **每次进入 `WebViewActivity` 自动保存 16 条上下文**：作为翻页范围（Context），在 Activity 内翻页不依赖返回列表。
- **不引入 Intent 过大风险**：上下文数据足够小且稳定传递。

## 非目标（Non-goals）
- 不实现 WebView 的网页历史前进/后退（`goBack/goForward`），这里的“上一条/下一条”是指 **上一条/下一条内容（LinkItem）**。
- 暂不覆盖 RSS/主题/归档/搜索等其它入口；这些入口打开 WebView 时不提供上下文，则不支持翻页。
- 暂不做跨进程长期保存阅读队列（进程被杀后允许翻页失效或降级）。

## 术语与定义
- **内容（Content）**：首页列表中的一条 `LinkItem`。
- **上下文（Context）**：围绕当前内容选取的一个有序子序列，最多 16 条，用于 `WebViewActivity` 内翻页。
- **当前索引（context_index）**：当前内容在上下文数组中的位置。

## 总体方案概览
方案采用“**Intent 携带 16 条 linkId 上下文**”的方式：
- 首页列表点击时，从“当前展示序列”中抽取 16 条 `linkId` + 当前 index。
- 通过 Intent 传入 `WebViewActivity`。
- `WebViewActivity` 增加“上一条/下一条”按钮：根据 `context_ids` 与 `context_index` 查找相邻 `linkId`，通过 `LinkDao` 获取 URL 后 `loadUrl`。

该方案优点：
- 与“当前列表里的下一条”强一致（上下文来自点击时的真实展示序列）。
- Intent 体积可控（16 个 `long` + 少量字段）。
- 不依赖全局 session 管理器，第一版实现成本低、风险小。

## 数据设计
### Intent 协议（首页 -> WebViewActivity）
`WebViewActivity` 继续支持现有字段，同时新增以下 extras（仅首页入口提供）：
- **`url`**：`String`，当前条目 URL（兼容历史逻辑，仍保留）。
- **`context_ids`**：`long[]`，长度 `1..16`，为上下文内的 `LinkItem.id`（有序）。
- **`context_index`**：`int`，当前条在 `context_ids` 的索引。
- **`context_source`**：`String`，固定 `"home"`（用于区分来源与降级策略）。

约束：
- `context_ids.length <= 16`
- `0 <= context_index < context_ids.length`

为什么选择 `linkId` 而不是 URL：
- URL 可能被重定向/规范化/替换；`linkId` 稳定。
- 便于翻页时更新点击次数、读取状态、备注等与内容绑定的属性（通过 DAO）。

### WebViewActivity 内状态
`WebViewActivity` 运行时维护：
- `long[] contextIds`
- `int contextIndex`
- `Long currentLinkId`（可选，用于日志与一致性校验）
- `String currentUrl`（已有）

## 上下文生成规则（首页点击时计算）
### 核心原则
上下文必须来自“用户当时看到的首页列表顺序”。因此上下文生成应基于“点击时的展示数据源（Adapter 当前 items）”，而不是重新从数据库查询排序。

### 过滤规则（重要）
首页列表数据可能包含非内容项（例如日期分组 header、置顶 header 等）。生成上下文时需要：
- **只保留 `LinkItem`**。
- 丢弃 header / 分隔符 / 其他非 `LinkItem` 项。

### 截取策略：中心窗口（推荐）
为确保“上一条/下一条”都可用，选择以当前点击项为中心的窗口：
- 设 `pos` 为当前点击 `LinkItem` 在“纯 `LinkItem` 序列”中的位置。
- 取窗口：
  - `start = max(0, pos - 7)`
  - `end = min(size, start + 16)`
  - 若 `end - start < 16` 且 `start > 0`，则反向补齐：`start = max(0, end - 16)`
- 上下文为 `[start, end)`（长度 `<= 16`）。
- `context_index = pos - start`

说明：
- 当列表长度不足 16 时，context 取全量。
- 当点击项靠近头/尾时，窗口自动向另一侧扩展以尽量取满 16。

## WebViewActivity 交互设计
### UI 放置
第一版建议放在 Toolbar（减少布局改动）：
- **上一个**：向左箭头图标
- **下一个**：向右箭头图标

也可选底部双按钮（阅读器风格），但涉及布局更大改动；第一版推荐 Toolbar。

### 按钮可用状态
- 当 `contextIds == null` 或 `contextIds.length < 2`：隐藏按钮或置灰。
- `contextIndex == 0`：上一个置灰。
- `contextIndex == last`：下一个置灰。

### 点击行为（上一条/下一条）
点击“下一条”为例：
- `targetIndex = contextIndex + 1`
- `targetLinkId = contextIds[targetIndex]`
- 通过 `LinkDao` 查询 `targetLinkId` 拿到最新 URL（以及可选 title）
- 更新 `contextIndex = targetIndex`、`currentLinkId = targetLinkId`、`currentUrl = newUrl`
- 调用 WebView 加载新 URL（同一个 Activity 内完成，不返回列表）
- 更新按钮置灰状态
- 更新点击次数统计（与“列表点击打开”一致的口径）

点击“上一条”同理。

## 生命周期与恢复策略
### 屏幕旋转/配置变更
在 `onSaveInstanceState` 保存：
- `context_ids`
- `context_index`
- `current_link_id`（可选）
- `current_url`（已有/可选）

恢复时：
- 若 `context_ids` 合法，则恢复按钮状态与 index；
- 否则降级为无上下文模式（隐藏/置灰翻页按钮）。

### 进程被系统杀死
第一版允许降级：
- 若恢复时拿不到 `context_ids`，则不支持翻页（按钮隐藏/置灰）。

后续增强（非第一版）可考虑：
- 引入 `ReadingSessionManager` + `sessionId`，或
- 用“首页当前筛选参数 + currentLinkId”重建相邻 16 条（成本更高，且需要严格对齐列表排序/过滤）。

## 与现有功能的兼容性说明
### 点击次数统计
当前点击次数更新发生在列表点击打开时。新增翻页后，需要确保：
- 通过按钮翻到下一条/上一条时，同样更新对应 `LinkItem` 的点击次数（口径一致）。

### WebView 存档缓存（WebViewManager）
项目存在“存档返回”保存 WebView 实例的机制。第一版翻页建议：
- **翻页不自动触发存档**（避免快速翻页导致缓存膨胀与管理复杂化）。
- 存档仍由用户显式菜单触发即可。

### 外部打开/桌面快捷方式
外部入口（分享、ACTION_VIEW、快捷方式）可能不具备“首页列表上下文”。策略：
- 不传 `context_ids`；
- `WebViewActivity` 检测无上下文时隐藏/置灰翻页按钮，并可提示“仅支持从首页列表进入时翻页”。

## 性能与稳定性
- Intent 大小：`16 * 8 bytes` 级别（long 数组）+ 少量字段，远低于 Binder 限制。
- DAO 查询：每次翻页最多一次查 URL（可加简单缓存：当前 16 条提前查出 URL，属于可选优化）。
- WebView 加载：同一 Activity 内 `loadUrl`，不会创建新 Activity，避免返回栈膨胀。

## 日志与排障（建议）
为便于排障，可打印关键点（注意不要输出敏感数据）：
- 进入 WebView 时：`context_source/context_ids.length/context_index/current_link_id`
- 翻页点击：`fromIndex -> toIndex, target_link_id`
- DAO 查询失败/URL 为空：打印 `target_link_id` 并降级提示

## 验收标准（Acceptance Criteria）
- 从首页列表点击任意条进入 `WebViewActivity`：
  - 若列表总数 >= 2：显示“上一个/下一个”按钮；首尾置灰正确。
  - 点击“下一条/上一条”能在不退出 Activity 的情况下加载对应内容。
  - “下一条”与首页列表顺序一致（至少在 16 条窗口内严格一致）。
- 从外部打开 WebView（分享/快捷方式）：
  - 翻页按钮隐藏或置灰，不影响现有返回逻辑。

## 后续扩展（Roadmap）
- 覆盖 RSS/主题/归档/搜索入口：各入口生成各自的上下文（仍可保持 16 条窗口）。
- 引入 session 管理器：支持更大上下文、跨页面跳转保持队列。
- 提供“回到列表当前位置/目录”按钮：一键回到首页并定位原条目（需要额外的定位参数）。

---

## 补充：右侧悬浮上下箭头变体（2026-08-23）

在原有底部横排"上一篇/下一篇"之外，新增了一组右侧悬浮、上下排列的圆形箭头按钮，作为可切换的替代 UI。

- **复用面**：按钮的点击处理直接复用现有的 `navigateToPrevious()` / `navigateToNext()`，可见性切换复用 `initControlsIfNeeded()` 与 `showControlsTemporarily()`，触发自动隐藏的 Handler 也共用 `hideControlsRunnable`。
- **新增面**：
  - `activity_webview.xml` 内新增 `floating_nav_controls` FrameLayout，含两个 `ImageButton`（`button_floating_previous` / `button_floating_next`）。
  - `WebViewActivity` 字段 `floatingNavControls` / `buttonFloatingPrevious` / `buttonFloatingNext`。
  - 工具方法 `shouldShowBottomNav()` / `shouldShowFloatingNav()`，读取 `SharedPreferences("settings")` 中的 `nav_button_style`，默认值 `"both"`。
- **用户配置**：设置页新增"翻页按钮显示方式"三选一 RadioGroup（底部横排 / 右侧悬浮 / 都显示），通过 `nav_button_style` key 持久化；切换后**下次**进入 `WebViewActivity` 生效。
- **互操作边界**：菜单项"显示/隐藏导航控件"(`action_toggle_navigation` / `KEY_NAV_CONTROLS_HIDDEN`)只影响底部横排，与悬浮无关；悬浮始终按设置项与丝滑模式规则进退。
- **设计文档**：`docs/superpowers/specs/2026-08-23-webview-floating-prev-next-design.md`
- **实施计划**：`docs/superpowers/plans/2026-08-23-webview-floating-prev-next.md`

---

## 补充：悬浮随机跳转变体（2026-08-23）

在 2026-08-23 完成的悬浮上下箭头变体之上，再新增一个独立的"随机跳转"圆形按钮，点击后从当前 `context_ids` 中随机选一个不等于 `currentIndex` 的条目跳转。

- **复用面**：随机算法产生 target index 后，直接调用现有 `loadByContextIndex(int)`，自带 `isNavigating` 守护与 Toast 错误处理。随机按钮是 `floating_nav_controls` 容器的子节点，可见性自动继承容器（无需新增任何 setVisibility 调用）。
- **新增面**：
  - `drawable/ic_random.xml`（shuffle 矢量图标，white，24dp viewport）。
  - `activity_webview.xml` 内新增 `button_floating_random` ImageButton（`marginTop="24dp"` 形成与上下组 24dp 视觉留白，凸显独立）。
  - WebViewActivity 字段 `buttonFloatingRandom` / `random(java.util.Random)`，新方法 `navigateToRandom()`。
- **设计边界**：
  - `contextIds.length <= 1` 时 Toast "无可随机条目"，不响应。
  - 快速连点由 `loadByContextIndex` 内部的 `isNavigating` 守卫处理。
  - 没有新增设置项；随机按钮属于悬浮组，沿用 `nav_button_style`。
- **设计文档**：`docs/superpowers/specs/2026-08-23-webview-random-jump-design.md`
- **实施计划**：`docs/superpowers/plans/2026-08-23-webview-random-jump.md`


