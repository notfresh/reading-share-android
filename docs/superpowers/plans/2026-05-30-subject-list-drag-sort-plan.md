# 主题列表拖拽排序实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为主题列表添加拖拽排序功能，长按拖拽调整顺序，排序结果持久化保存。

**Architecture:** 复用 SubjectDetailActivity 的 ItemTouchHelper 拖拽实现，在 SubjectFragment 中添加排序模式开关，Subject 模型新增 orderIndex 字段支持持久化排序。

**Tech Stack:** Android ItemTouchHelper, SQLite, RecyclerView

---

## 文件变更清单

| 文件 | 变更内容 |
|------|----------|
| `core/model/Subject.java` | 新增 `orderIndex` 字段及 getter/setter |
| `db/LinkDbHelper.java` | 新增 `COLUMN_SUBJECT_ORDER_INDEX` 常量，升级时 ALTER TABLE 添加列 |
| `db/SubjectDao.java` | 新增 `updateSubjectsOrderIndex()` 批量更新，`getAllSubjects()` 改用 orderIndex 排序 |
| `res/menu/subject_menu.xml` | 新增"排序"菜单项 |
| `adapter/SubjectAdapter.java` | 新增 `setSortMode()` 方法，禁用排序模式下的操作菜单 |
| `ui/subject/SubjectFragment.java` | 排序模式状态管理、Toolbar 动态切换、ItemTouchHelper 拖拽逻辑 |

---

### Task 1: Subject 模型新增 orderIndex 字段

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/core/model/Subject.java`

**Steps:**

- [ ] **Step 1: 添加 orderIndex 字段和 getter/setter**

在 `Subject.java` 中找到 `private long createTime;` 行，在其下方添加：

```java
private int orderIndex;              // 排序索引（间隔值：0, 10, 20, 30...）
```

在 `getCreateTime()` 方法后添加：

```java
public int getOrderIndex() {
    return orderIndex;
}

public void setOrderIndex(int orderIndex) {
    this.orderIndex = orderIndex;
}
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/person/notfresh/readingshare/core/model/Subject.java
git commit -m "feat(subject): add orderIndex field for drag sort support"
```

---

### Task 2: 数据库层 - LinkDbHelper 新增 order_index 列

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/db/LinkDbHelper.java`

**Steps:**

- [ ] **Step 1: 添加常量**

在 `COLUMN_SUBJECT_CREATE_TIME` 定义下方添加：

```java
public static final String COLUMN_SUBJECT_ORDER_INDEX = "order_index";
```

- [ ] **Step 2: 修改建表语句**

将 `SQL_CREATE_SUBJECTS` 修改为：

```java
private static final String SQL_CREATE_SUBJECTS =
        "CREATE TABLE " + TABLE_SUBJECTS + " (" +
                COLUMN_SUBJECT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_SUBJECT_TITLE + " TEXT NOT NULL, " +
                COLUMN_SUBJECT_DESCRIBE + " TEXT, " +
                COLUMN_SUBJECT_CREATE_TIME + " INTEGER NOT NULL, " +
                COLUMN_SUBJECT_ORDER_INDEX + " INTEGER DEFAULT 0)";
```

- [ ] **Step 3: 添加升级逻辑**

在 `onUpgrade` 方法中 `if (oldVersion < 11)` 的代码块之后添加：

```java
if (oldVersion < 14) {
    // 版本14：主题表增加排序字段
    db.execSQL("ALTER TABLE " + TABLE_SUBJECTS + " ADD COLUMN " + COLUMN_SUBJECT_ORDER_INDEX + " INTEGER DEFAULT 0");
    Log.d("LinkDbHelper", "Added subject order_index column");
}
```

**注意：** 需要同步更新 `DATABASE_VERSION` 为 14。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/person/notfresh/readingshare/db/LinkDbHelper.java
git commit -m "feat(db): add order_index column to subjects table"
```

---

### Task 3: 数据库层 - SubjectDao 新增批量更新方法

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/db/SubjectDao.java`

**Steps:**

- [ ] **Step 1: 修改 cursorToSubject 方法，读取 orderIndex**

在 `cursorToSubject` 方法中添加一行：

```java
subject.setOrderIndex(cursor.getInt(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_SUBJECT_ORDER_INDEX)));
```

- [ ] **Step 2: 修改 getAllSubjects 查询，按 orderIndex 排序**

将 `LinkDbHelper.COLUMN_SUBJECT_CREATE_TIME + " DESC"` 改为：

```java
LinkDbHelper.COLUMN_SUBJECT_ORDER_INDEX + " ASC"
```

- [ ] **Step 3: 在 insertSubject 方法中插入 orderIndex**

在 `insertSubject` 方法中添加：

```java
values.put(LinkDbHelper.COLUMN_SUBJECT_ORDER_INDEX, subject.getOrderIndex());
```

注意 `subject.getOrderIndex()` 可能返回 0（默认值），这是正常的。

- [ ] **Step 4: 在 updateSubject 方法中更新 orderIndex**

在 `updateSubject` 方法的 `ContentValues` 中添加：

```java
values.put(LinkDbHelper.COLUMN_SUBJECT_ORDER_INDEX, subject.getOrderIndex());
```

- [ ] **Step 5: 新增批量更新 orderIndex 方法**

在 `updateSubject` 方法之后添加：

```java
/**
 * 批量更新主题的 orderIndex
 */
public void updateSubjectsOrderIndex(List<Subject> subjects) {
    if (subjects == null || subjects.isEmpty()) {
        return;
    }

    database.beginTransaction();
    try {
        for (Subject subject : subjects) {
            ContentValues values = new ContentValues();
            values.put(LinkDbHelper.COLUMN_SUBJECT_ORDER_INDEX, subject.getOrderIndex());
            database.update(
                    LinkDbHelper.TABLE_SUBJECTS,
                    values,
                    LinkDbHelper.COLUMN_SUBJECT_ID + " = ?",
                    new String[]{String.valueOf(subject.getId())}
            );
        }
        database.setTransactionSuccessful();
    } finally {
        database.endTransaction();
    }
}
```

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/person/notfresh/readingshare/db/SubjectDao.java
git commit -m "feat(dao): add batch update orderIndex method for subjects"
```

---

### Task 4: 菜单 - 新增排序菜单项

**Files:**
- Modify: `app/src/main/res/menu/subject_menu.xml`

**Steps:**

- [ ] **Step 1: 添加排序菜单项**

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">
    <item
        android:id="@+id/action_create_subject"
        android:icon="@android:drawable/ic_input_add"
        android:title="创建主题"
        app:showAsAction="ifRoom" />
    <item
        android:id="@+id/action_sort_subjects"
        android:icon="@android:drawable/ic_menu_sort_by_size"
        android:title="排序"
        app:showAsAction="ifRoom" />
</menu>
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/res/menu/subject_menu.xml
git commit -m "feat(menu): add sort action for subject list"
```

---

### Task 5: SubjectAdapter 新增排序模式

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/adapter/SubjectAdapter.java`

**Steps:**

- [ ] **Step 1: 添加 sortMode 字段和 setter**

在 `SubjectAdapter` 类开头添加字段：

```java
private boolean sortMode = false;
```

添加 setter 方法：

```java
public void setSortMode(boolean sortMode) {
    this.sortMode = sortMode;
}
```

- [ ] **Step 2: 修改 SubjectViewHolder 长按逻辑**

将长按监听器修改为：

```java
// 长按显示操作菜单（排序模式下禁用）
itemView.setOnLongClickListener(v -> {
    if (sortMode) {
        // 排序模式下不处理长按，交给 ItemTouchHelper 处理拖拽
        return false;
    }
    int position = getAdapterPosition();
    if (position != RecyclerView.NO_POSITION) {
        showActionMenu(v, subjects.get(position));
    }
    return true;
});
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/person/notfresh/readingshare/adapter/SubjectAdapter.java
git commit -m "feat(adapter): add sortMode to disable action menu during drag"
```

---

### Task 6: SubjectFragment 整合拖拽排序功能

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/ui/subject/SubjectFragment.java`

**Steps:**

- [ ] **Step 1: 添加 import**

```java
import androidx.recyclerview.widget.ItemTouchHelper;
```

- [ ] **Step 2: 添加字段**

```java
private boolean isSortMode = false;
private ItemTouchHelper itemTouchHelper;
private MenuItem sortMenuItem;
```

- [ ] **Step 3: 修改 onCreateOptionsMenu**

```java
@Override
public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
    inflater.inflate(R.menu.subject_menu, menu);
    sortMenuItem = menu.findItem(R.id.action_sort_subjects);
    super.onCreateOptionsMenu(menu, inflater);
}
```

- [ ] **Step 4: 修改 onOptionsItemSelected**

```java
@Override
public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    if (item.getItemId() == R.id.action_create_subject) {
        showCreateSubjectDialog();
        return true;
    } else if (item.getItemId() == R.id.action_sort_subjects) {
        toggleSortMode();
        return true;
    }
    return super.onOptionsItemSelected(item);
}
```

- [ ] **Step 5: 添加排序模式切换方法**

```java
private void toggleSortMode() {
    isSortMode = !isSortMode;
    adapter.setSortMode(isSortMode);

    if (isSortMode) {
        // 进入排序模式
        sortMenuItem.setTitle("完成");
        if (getToolbar() != null) {
            getToolbar().setTitle("排序中...");
        }
        itemTouchHelper.attachToRecyclerView(recyclerView);
    } else {
        // 退出排序模式
        sortMenuItem.setTitle("排序");
        if (getToolbar() != null) {
            getToolbar().setTitle(getString(R.string.app_name));
        }
        itemTouchHelper.attachToRecyclerView(null);
    }
}
```

添加获取 Toolbar 的辅助方法：

```java
private Toolbar getToolbar() {
    return requireActivity().findViewById(R.id.toolbar);
}
```

- [ ] **Step 6: 添加 onCreateView 中初始化 ItemTouchHelper**

在 `recyclerView.setLayoutManager` 之后添加：

```java
setupDragAndDrop();
```

添加 setupDragAndDrop 方法：

```java
private void setupDragAndDrop() {
    ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView,
                             @NonNull RecyclerView.ViewHolder viewHolder,
                             @NonNull RecyclerView.ViewHolder target) {
            int fromPos = viewHolder.getAdapterPosition();
            int toPos = target.getAdapterPosition();

            List<Subject> items = adapter.getSubjects();
            if (fromPos < 0 || fromPos >= items.size() || toPos < 0 || toPos >= items.size()) {
                return false;
            }

            Subject draggedItem = items.get(fromPos);
            items.remove(fromPos);
            items.add(toPos, draggedItem);
            adapter.notifyItemMoved(fromPos, toPos);

            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            // 不需要实现
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView,
                             @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);

            // 拖拽结束，按当前顺序重新分配 orderIndex 并保存
            List<Subject> items = adapter.getSubjects();
            for (int i = 0; i < items.size(); i++) {
                items.get(i).setOrderIndex(i * SubjectUtil.ORDER_INTERVAL);
            }

            subjectDao.updateSubjectsOrderIndex(items);
            Log.d(TAG, "拖拽排序完成，已更新 " + items.size() + " 个主题的 orderIndex");
        }
    };

    itemTouchHelper = new ItemTouchHelper(callback);
}
```

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/person/notfresh/readingshare/ui/subject/SubjectFragment.java
git commit -m "feat(subject): add drag sort support to subject list"
```

---

## 自检清单

- [ ] Spec 覆盖检查：每个设计需求都有对应的实现步骤
- [ ] 类型一致性：orderIndex 使用 int 类型，与 SubjectItem 保持一致
- [ ] 占位符检查：无 TBD/TODO/未完成的步骤
- [ ] 依赖正确：SubjectAdapter 在 SubjectFragment 之前完成