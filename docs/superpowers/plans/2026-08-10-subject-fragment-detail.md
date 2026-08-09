# Subject Fragment Detail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Subject 详情逻辑迁移到 `SubjectFragment`，并将主题列表改造成用于切换和管理主题的弹窗。

**Architecture:** `SubjectFragment` 复用现有主题项详情逻辑，使用 MainActivity Toolbar 承载详情菜单；`SubjectListDialog` 复用 `SubjectAdapter` 处理主题选择和长按管理操作。上次查看主题只保留为入口记忆，失效时回退到主题列表弹窗。

**Tech Stack:** Android Java, AndroidX Fragment, RecyclerView, SQLite DAO, Gradle unit tests.

## Global Constraints

- 不修改数据库表结构和公共数据模型。
- 不改变主页/标签页向主题批量添加链接的现有流程。
- 不引入新的第三方依赖。
- 保持桌面快捷方式的旧 Activity 入口兼容。

### Task 1: Create the reusable subject list dialog

**Files:**
- Create: `app/src/main/java/person/notfresh/readingshare/ui/subject/SubjectListDialog.java`
- Reuse: `app/src/main/res/layout/dialog_select_subject.xml`
- Reuse: `app/src/main/java/person/notfresh/readingshare/adapter/SubjectAdapter.java`

- [ ] **Step 1:** Define a dialog callback carrying only the selected subject ID and expose `newInstance(long selectedSubjectId)`.
- [ ] **Step 2:** Wire `SubjectAdapter` selection to dismiss the dialog and invoke the callback.
- [ ] **Step 3:** Forward edit, delete, shortcut, and custom-icon actions to a host callback so the existing list behavior remains available.
- [ ] **Step 4:** Add a create button/menu action to open `CreateSubjectDialog` and reload the list.
- [ ] **Step 5:** Add drag sorting inside the dialog and persist order indexes through `SubjectDao`.
- [ ] **Step 6:** Verify the dialog compiles with the existing adapter and layout.

### Task 2: Move detail behavior into SubjectFragment

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/ui/subject/SubjectFragment.java`
- Modify: `app/src/main/res/layout/fragment_subject.xml`
- Modify: `app/src/main/res/menu/subject_detail_menu.xml`

- [ ] **Step 1:** Replace list initialization with detail initialization and load the last viewed subject ID.
- [ ] **Step 2:** Move subject-item adapter setup, DAO lifecycle, empty state, add/edit/delete/archive, and drag-sort behavior from `SubjectDetailActivity`.
- [ ] **Step 3:** Add toolbar actions for adding an item, switching subjects, and subject settings.
- [ ] **Step 4:** Open `SubjectListDialog` when there is no valid last subject or when switching subjects.
- [ ] **Step 5:** Refresh the detail view and save the selected subject ID after dialog selection.
- [ ] **Step 6:** Keep custom shortcut icon selection working from the dialog.
- [ ] **Step 7:** Change link opening so a single valid link opens directly, while multiple links retain context navigation.

### Task 3: Keep external entry points compatible

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/ui/subject/SubjectDetailActivity.java`
- Modify: `app/src/main/java/person/notfresh/readingshare/util/ShortcutUtil.java`

- [ ] **Step 1:** Change the legacy detail Activity to forward valid subject IDs to the Subject navigation destination or retain a minimal compatibility implementation where direct navigation cannot be represented.
- [ ] **Step 2:** Ensure invalid/deleted shortcut subjects return to the Subject list flow without repeated launches.
- [ ] **Step 3:** Remove obsolete source-specific behavior from the active path.

### Task 4: Validate migration

**Files:**
- Test: `app/src/test/java/person/notfresh/readingshare/ui/subject/SubjectEntryManagerTest.java`

- [ ] **Step 1:** Run `testDebugUnitTest`.
- [ ] **Step 2:** Run `:app:assembleDebug`.
- [ ] **Step 3:** Inspect the diff for duplicate navigation or unclosed DAO resources.