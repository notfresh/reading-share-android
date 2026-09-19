# 浏览历史 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 WebView 增加独立、可清空、无限制数量的浏览历史，并从 WebView 菜单和设置页进入同一个历史页面。

**Architecture:** 使用现有 `links.db` 新增 `links_history` 表和独立 DAO 方法；WebView 主框架 `onPageFinished()` 记录最终 URL、标题和时间。新增 `LinksHistoryActivity` 与适配器统一展示、打开、删除和清空逻辑，两个入口只负责启动 Activity。

**Tech Stack:** Android Java、SQLiteOpenHelper、RecyclerView、AppCompat、现有 Navigation/Activity 体系。

## Global Constraints

- 历史表名固定为 `links_history`。
- 历史不限制条数，用户手动清空。
- 只记录 WebView 主框架成功完成的页面，不记录空 URL、`about:blank` 或资源请求。
- 相同 URL 的多次访问保留多条记录。
- 数据库版本从 15 升到 16，只新增表，不删除或重建现有表。
- WebView 菜单和设置页复用同一个历史 Activity。
- 保持现有 Java/Android 代码风格，修改范围集中。

---

### Task 1: 创建浏览历史表和数据访问接口

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/db/LinkDbHelper.java`
- Modify: `app/src/main/java/person/notfresh/readingshare/db/LinkDao.java`
- Create: `app/src/main/java/person/notfresh/readingshare/model/LinkHistoryItem.java`

**Interfaces:**
- `LinkHistoryItem(long id, String title, String url, long visitedAt)`
- `LinkDao.insertLinkHistory(String title, String url, long visitedAt)`
- `LinkDao.getAllLinkHistory()` returns `List<LinkHistoryItem>` ordered by `visited_at DESC`
- `LinkDao.deleteLinkHistory(long id)` returns `boolean`
- `LinkDao.clearLinkHistory()` returns deleted row count

- [ ] **Step 1: Add the model with immutable getters.**
- [ ] **Step 2: Add `TABLE_LINKS_HISTORY`, its columns, and `CREATE TABLE` SQL.**
- [ ] **Step 3: Increase `DATABASE_VERSION` to 16.**
- [ ] **Step 4: In `onUpgrade`, create the new table when `oldVersion < 16`; do not touch existing tables.**
- [ ] **Step 5: Add DAO insert/query/delete/clear methods using `SQLiteDatabase` APIs.**
- [ ] **Step 6: Run `get_errors` on the three Java files.**

Expected result: existing databases upgrade without losing `links`; history CRUD is available through `LinkDao`.

---

### Task 2: Record completed WebView page visits

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/WebViewActivity.java`

**Interfaces:**
- Add a private `recordLinkHistory(WebView view, String url)` helper.

- [ ] **Step 1: Call the helper from `onPageFinished()` only when `view == webView`.**
- [ ] **Step 2: Skip null/empty URLs and `about:blank`.**
- [ ] **Step 3: Resolve title from `view.getTitle()`, then `pageTitleCache`, then URL.**
- [ ] **Step 4: Insert asynchronously with `LinkDao`, logging failures without affecting the page.**

Expected result: each completed main-frame page visit creates one history record, including redirects.

---

### Task 3: Build shared history list UI

**Files:**
- Create: `app/src/main/java/person/notfresh/readingshare/LinksHistoryActivity.java`
- Create: `app/src/main/java/person/notfresh/readingshare/adapter/LinksHistoryAdapter.java`
- Create: `app/src/main/res/layout/activity_links_history.xml`
- Create: `app/src/main/res/layout/item_link_history.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Activity loads `LinkDao.getAllLinkHistory()` on a worker thread and updates RecyclerView on the main thread.
- Adapter callback `onHistoryClick(LinkHistoryItem item)` opens `WebViewActivity` with `url`.

- [ ] **Step 1: Add list and empty-state layouts with title, URL, visited time, delete affordance, and clear button.**
- [ ] **Step 2: Add adapter binding and single-record delete callback.**
- [ ] **Step 3: Add Activity, toolbar title, async load, empty state, delete confirmation, and clear-all confirmation.**
- [ ] **Step 4: Register Activity in the manifest.**
- [ ] **Step 5: Run file diagnostics for new Java/XML files.**

Expected result: one reusable history page supports opening, single deletion, and clearing all records.

---

### Task 4: Add both navigation entries

**Files:**
- Modify: `app/src/main/res/menu/webview_menu.xml`
- Modify: `app/src/main/java/person/notfresh/readingshare/WebViewActivity.java`
- Modify: `app/src/main/res/layout/fragment_slideshow.xml`
- Modify: `app/src/main/java/person/notfresh/readingshare/ui/settings/SettingFragment.java`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add `action_link_history` to the WebView menu.**
- [ ] **Step 2: Handle it by starting `LinksHistoryActivity`.**
- [ ] **Step 3: Add a settings button labeled “浏览历史”.**
- [ ] **Step 4: Handle the settings button by starting the same Activity.**
- [ ] **Step 5: Run file diagnostics for all changed Java/XML files.**

Expected result: both entry points open the same history page without duplicating UI logic.

---

### Task 5: Verify database upgrade and user flow

**Files:**
- Test/verify: existing Gradle test and build configuration

- [ ] **Step 1: Run ` .\gradlew.bat :app:testDebugUnitTest` from PowerShell.**
- [ ] **Step 2: Run ` .\gradlew.bat :app:assembleDebug`.**
- [ ] **Step 3: Install with `adb install -r app\build\outputs\apk\debug\app-debug.apk`.**
- [ ] **Step 4: Manually verify: open WebView, finish navigation, open history from WebView menu, open history from settings, reopen an item, delete one, and clear all.**
- [ ] **Step 5: Check `adb logcat` for database upgrade or Activity crash errors.**

Expected result: tests/build pass and the two entry points share correct history behavior.
