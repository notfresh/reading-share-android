# 搜索历史下拉功能 (2026-06-09)

## 发现
- 项目是 Android Java 应用（duxiang-android），包名 person.notfresh.readingshare
- HomeFragment 已有搜索 EditText（`searchEditText`），当前是 afterTextChanged 里实时 adapter.filter()，无防抖无历史
- 现有持久化分层：DAO 用 SQLite（LinkDbHelper），设置项用 SharedPreferences（命名 + Activity-private 两种模式）
- 已有可复用资源：ic_close.xml（删除按钮）、search_background.xml
- 测试栈：JUnit 4.13.2，无 Robolectric —— 涉及 SharedPreferences/JSON 的类难单测
- HomeFragment 已 ~2400 行，新功能加在文件末尾的"搜索历史下拉"section 内，避免改动既有 tag 逻辑

## 学到
- **"程序化 setText 不触发 TextWatcher"** —— Android 跟 web 一样需要手动同步副作用。点选历史项后必须显式调用 performSearch(kw)
- **PopupWindow 模拟 mousedown preventDefault 的 Android 三件套**：popup `setFocusable(false)` + `setOutsideTouchable(false)` + 在内容 RecyclerView 上 setOnTouchListener 取消 pending hide runnable
- **持久化层用 SharedPreferences + org.json** 对小数据量（≤20 条）足够，不必上 SQLite
- **纯逻辑 / 持久化分层** 让 LRU 淘汰能用 JUnit 完整测试边界条件（`>` vs `>=`、pinned 不参与计数等）
- **TDD 中"过度断言"是常见坑**：原 plan 里的 `evictLRU_dropsOldestUnpinnedWhenExceeds` 一开始断言了 survivors 顺序，但 spec 的 `evictLRU` 只淘汰不排序。修正：让 test 只断言集合内容（u1 被淘汰，u2/u3 保留），不碰顺序
- **spec 6.4 "maxCount 收缩时立即淘汰"** 是 SearchHistoryManager.setMaxCount 的内置行为（不是 HomeFragment 调的），这层抽象让 maxCount 的多种调用点都自动正确
- **subagent 的 DONE_WITH_CONCERNS 是真实信号** —— implementer 主动发现了 spec/test 不一致，省了一轮 review
- **pre-existing uncommitted changes**（SubjectFragment.java 修改、done-sync → done-sink 重命名）需要在每个 commit 用 `git add <specific-file>` 显式指定

## 完成
- 8 个 feat commit（bf44452 → 7422e00）
- 11 个 JUnit 测试全 PASS（SearchHistoryLogicTest 覆盖 sort/upsert/evict 三大方法）
- Debug APK 编译成功（./gradlew :app:assembleDebug BUILD SUCCESSFUL）
- spec section 三 的 10 项功能行为全部覆盖（项 7 因无 × 清空按钮正确 N/A）
- spec section 九 的 9 个坑全部规避
- 4 个新 Java 类：SearchHistoryItem / SearchHistoryLogic / SearchHistoryManager / SearchHistoryAdapter
- 3 个修改文件：HomeFragment.java（+139 行）+ SettingFragment.java（+22 行）+ fragment_slideshow.xml（+18 行）
- 关键设计决策：PopupWindow setFocusable(false) + 200ms blur delay + RecyclerView touch 取消 hide runnable 的三件套等价于 web 的 mousedown preventDefault
- 文档：spec 写在 docs/superpowers/specs/，plan 写在 docs/superpowers/plans/，完整 11 项 manual smoke test 在 plan Task 9
