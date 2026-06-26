# 搜索框输入态完善 (2026-06-09)

## 发现
- 上一轮（2026-06-08）已经实现的搜索历史下拉功能：4 个核心类（SearchHistoryItem / Logic / Manager / Adapter），PopupWindow + 200ms blur delay + 300ms 防抖搜索，11 个 JUnit 测试覆盖纯逻辑层
- HomeFragment.java 已经长到 ~2488 行，新功能依然加在文件末尾"搜索历史下拉"section 内（行 110-130 字段区、行 350-510 初始化、行 750-800 onDestroyView），不污染既有 tag 逻辑
- 已有的可复用资源：`search_background.xml`（浅灰圆角）、`ic_close.xml`（白色 ×，菜单用）、`strings.xml` 搜索历史下拉 section（5 条 string）
- 项目没有 buildSrc / convention plugin，AGP 用 ViewBinding 自动从 `android:id="@+id/foo"` 生成 `binding.foo` 字段
- pre-existing uncommitted 干扰模式：每次 add 都得用具体文件名（`.claude/settings.local.json` + `SubjectFragment.java` + `done-sync`→`done-sink` 重命名）

## 学到
- **"单一真源方法" 适合多入口控制同一组 UI 状态**：`updateInputChrome()` 集中处理 × 按钮和下拉显隐，5+ 个调用点（focus / text watcher / × click / performSearch / 3 个 adapter callback）都走它——以后再加新 chrome 元素（比如搜索图标）只需要在 updateInputChrome 加一行
- **行为表 + 三个澄清问题** 就能让范围爆炸的需求变成 2 个 task 的小改动：用户给的 3 条要求很清晰，加上"× 怎么点 / 删完最后一条怎么处理 / 图标怎么准备" 3 个澄清，spec 写出来就接近可落地状态
- **"先 brainstorming 再 subagent-driven-development" 的全流程跑通一遍**：brainstorming 提 3 个澄清 → 写 spec 并自审 → 用户审 spec → writing-plans 2 个 task → 派 subagent → 2 轮 review（spec + quality） → 1 个 fix amend → final review。总计 7 个 subagent 调用 + 2 个交互节点（澄清、用户审 spec）
- **"defensive symmetry" 是 code review 的金标准发现**：× click handler 没有 `removeCallbacks(debounceSearchRunnable)`，跟 onItemClick / blur 路径不对称；当前代码是安全的（performSearch 守门 `!text.isEmpty()`），但属于"latent foot-gun"——这种"加 1 行免疫未来重构"的修复值得做
- **showHistoryPopup 里"先 refresh 再 guard" 比 "先 guard 再 refresh" 重要**：先 refresh 一次，外部刚 deleteKeyword 完才能在 isEmpty 守门里反映。这是"删除最后一条关下拉"这个行为的 load-bearing 细节，final review 单独指出来了
- **subagent 改用 amend 而非新 commit 是对的**：1 行防御性修复如果用新 commit 会污染历史，amend 折叠进原 commit 让"feat(search-history): wire × clear button + empty-state guard"这个原子单元保持干净
- **`git add <specific-file>` 是 pre-existing 干扰的应对模式**：每个 subagent prompt 都要显式提醒，不然 amend/new commit 容易把 `.claude/settings.local.json` 带进去
- **Gradle wrapper 在 Windows bash 里有怪问题**：subagent 第一次试 `.\gradlew` 失败、改 `./gradlew --offline` 就过。后续 plan 默认就用 bash 风格 `./gradlew`
- **plan 里 plan section "保留什么" 也要写清楚**：spec 显式说保留 SearchHistoryAdapter 的 TYPE_EMPTY 分支和 item_search_history_empty.xml（HomeFragment 不触发，但 adapter 自身保持健壮）——这种"故意不删"的决定也要在 plan 里写明，避免 implementer 误判为"未完成"

## 完成
- brainstorming + writing-plans 全流程跑通：3 个澄清问题 → spec 自审 4 项 → 用户审 spec → 2 个 task plan
- 2 个 commit（3b81e88 资源 + c9167fef 逻辑，1 个 amend 含 1 行 fix）
- 4 个文件改动：新建 `ic_clear.xml`（24dp × 深灰 #757575）+ 修改 `strings.xml`（+1 string）+ `fragment_home.xml`（EditText 包进 FrameLayout，加 ImageButton 32×32dp end 对齐）+ HomeFragment.java（+47 行，含 updateInputChrome 方法 + 6 个调用点 + onDestroyView 清理）
- 7 轮 subagent review 全部通过：Task 1 spec ✅ + quality ✅ + Task 2 spec ✅ + quality ✅（1 fix amend）+ fix re-review ✅ + final review ✅
- 6 个 manual smoke case 全部有 code path 支持（final review 行为覆盖矩阵 6/6）
- 文档：spec 写到 `docs/superpowers/specs/2026-06-09-search-history-input-chrome-design.md`，plan 写到 `docs/superpowers/plans/2026-06-09-search-history-input-chrome-plan.md`
- 关键设计：单一真源 `updateInputChrome()` + `showHistoryPopup` 双防御（isEmpty guard + refresh 重读）+ 防御对称（× handler 也调 removeCallbacks）
