# 文档书签与阅读进度保存实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 PDF 阅读器增加书签（带备注）和自动保存/恢复阅读进度功能

**Architecture:** 新建独立的 `document_bookmarks` 数据库表存储书签，复用现有 `config` 表 KV 存储记录阅读进度；`TocAdapter` 扩展为三种视图类型，侧边栏增加书签页签

**Tech Stack:** Android SQLite, PdfRenderer, RecyclerView, AlertDialog

---

### Task 1: 新增 BookmarkItem 模型类

**Files:**
- Create: `app/src/main/java/person/notfresh/readingshare/model/BookmarkItem.java`

- [ ] **Step 1: 创建 BookmarkItem.java**

```java
package person.notfresh.readingshare.model;

public class BookmarkItem {
    private long id;
    private long documentId;
    private int pageIndex;
    private String note;
    private long createdAt;

    public BookmarkItem() {}

    public BookmarkItem(long documentId, int pageIndex, String note) {
        this.documentId = documentId;
        this.pageIndex = pageIndex;
        this.note = note;
        this.createdAt = System.currentTimeMillis();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getDocumentId() { return documentId; }
    public void setDocumentId(long documentId) { this.documentId = documentId; }
    public int getPageIndex() { return pageIndex; }
    public void setPageIndex(int pageIndex) { this.pageIndex = pageIndex; }
    public int getPageNumber() { return pageIndex + 1; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 2: 编译确认**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/person/notfresh/readingshare/model/BookmarkItem.java
git commit -m "feat: add BookmarkItem model class for PDF bookmarks"
```

---

### Task 2: 数据库升级 — 新建 bookmarks 表

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/db/LinkDbHelper.java`

- [ ] **Step 1: 在 LinkDbHelper 中新增常量**

在 `COLUMN_DOC_CLICK_COUNT` 常量之后（第 53 行之后）添加：

```java
// 书签表
public static final String TABLE_BOOKMARKS = "document_bookmarks";
public static final String COLUMN_BOOKMARK_ID = "_id";
public static final String COLUMN_BOOKMARK_DOC_ID = "document_id";
public static final String COLUMN_BOOKMARK_PAGE_INDEX = "page_index";
public static final String COLUMN_BOOKMARK_NOTE = "note";
public static final String COLUMN_BOOKMARK_CREATED_AT = "created_at";
```

- [ ] **Step 2: 新增建表 SQL**

在 `SQL_CREATE_DOCUMENTS` 之后添加：

```java
private static final String SQL_CREATE_BOOKMARKS =
        "CREATE TABLE " + TABLE_BOOKMARKS + " (" +
                COLUMN_BOOKMARK_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_BOOKMARK_DOC_ID + " INTEGER NOT NULL, " +
                COLUMN_BOOKMARK_PAGE_INDEX + " INTEGER NOT NULL, " +
                COLUMN_BOOKMARK_NOTE + " TEXT, " +
                COLUMN_BOOKMARK_CREATED_AT + " INTEGER NOT NULL, " +
                "FOREIGN KEY (" + COLUMN_BOOKMARK_DOC_ID + ") REFERENCES " + TABLE_DOCUMENTS + "(" + COLUMN_DOC_ID + "))";
```

- [ ] **Step 3: 在 onCreate 中执行建表**

在 `db.execSQL(SQL_CREATE_DOCUMENTS);` 之后添加：

```java
db.execSQL(SQL_CREATE_BOOKMARKS);
```

- [ ] **Step 4: 在 onUpgrade 中处理版本升级**

在 `onUpgrade` 方法中，`oldVersion < 10` 的 `if` 块之前添加：

```java
if (oldVersion < 13) {
    db.execSQL(SQL_CREATE_BOOKMARKS);
    Log.d("LinkDbHelper", "Created bookmarks table");
}
```

- [ ] **Step 5: 更新 DATABASE_VERSION 为 13**

将 `private static final int DATABASE_VERSION = 12;` 改为 `13`。

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/person/notfresh/readingshare/db/LinkDbHelper.java
git commit -m "feat: add document_bookmarks table and upgrade DB to v13"
```

---

### Task 3: DocumentDao 新增书签和进度方法

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/db/DocumentDao.java`

需要先 import：
```java
import person.notfresh.readingshare.model.BookmarkItem;
```

- [ ] **Step 1: 新增 addBookmark 方法**

```java
/**
 * 添加书签
 * @return bookmarkId，失败返回 -1
 */
public long addBookmark(long documentId, int pageIndex, String note) {
    try {
        ContentValues values = new ContentValues();
        values.put(LinkDbHelper.COLUMN_BOOKMARK_DOC_ID, documentId);
        values.put(LinkDbHelper.COLUMN_BOOKMARK_PAGE_INDEX, pageIndex);
        values.put(LinkDbHelper.COLUMN_BOOKMARK_NOTE, note);
        values.put(LinkDbHelper.COLUMN_BOOKMARK_CREATED_AT, System.currentTimeMillis());
        return database.insert(LinkDbHelper.TABLE_BOOKMARKS, null, values);
    } catch (Exception e) {
        Log.e(TAG, "添加书签失败", e);
        return -1;
    }
}
```

- [ ] **Step 2: 新增 deleteBookmark 方法**

```java
/**
 * 删除书签
 * @return 是否成功
 */
public boolean deleteBookmark(long bookmarkId) {
    try {
        int rows = database.delete(
                LinkDbHelper.TABLE_BOOKMARKS,
                LinkDbHelper.COLUMN_BOOKMARK_ID + " = ?",
                new String[]{String.valueOf(bookmarkId)}
        );
        return rows > 0;
    } catch (Exception e) {
        Log.e(TAG, "删除书签失败", e);
        return false;
    }
}
```

- [ ] **Step 3: 新增 getBookmarksByDocument 方法**

```java
/**
 * 获取指定文档的所有书签，按创建时间升序
 */
public List<BookmarkItem> getBookmarksByDocument(long documentId) {
    List<BookmarkItem> bookmarks = new ArrayList<>();
    Cursor cursor = database.query(
            LinkDbHelper.TABLE_BOOKMARKS,
            null,
            LinkDbHelper.COLUMN_BOOKMARK_DOC_ID + " = ?",
            new String[]{String.valueOf(documentId)},
            null,
            null,
            LinkDbHelper.COLUMN_BOOKMARK_CREATED_AT + " ASC"
    );
    if (cursor.moveToFirst()) {
        do {
            BookmarkItem item = new BookmarkItem();
            item.setId(cursor.getLong(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_BOOKMARK_ID)));
            item.setDocumentId(cursor.getLong(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_BOOKMARK_DOC_ID)));
            item.setPageIndex(cursor.getInt(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_BOOKMARK_PAGE_INDEX)));
            int noteIdx = cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_BOOKMARK_NOTE);
            if (!cursor.isNull(noteIdx)) {
                item.setNote(cursor.getString(noteIdx));
            }
            item.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_BOOKMARK_CREATED_AT)));
            bookmarks.add(item);
        } while (cursor.moveToNext());
    }
    cursor.close();
    return bookmarks;
}
```

- [ ] **Step 4: 新增 saveReadingProgress 方法**

```java
/**
 * 保存阅读进度到 config 表
 */
public void saveReadingProgress(long documentId, int pageIndex) {
    try {
        String key = "reading_progress_" + documentId;
        ContentValues values = new ContentValues();
        values.put(LinkDbHelper.COLUMN_CONFIG_KEY, key);
        values.put(LinkDbHelper.COLUMN_CONFIG_VALUE, String.valueOf(pageIndex));
        database.delete(LinkDbHelper.TABLE_CONFIG,
                LinkDbHelper.COLUMN_CONFIG_KEY + " = ?",
                new String[]{key});
        database.insert(LinkDbHelper.TABLE_CONFIG, null, values);
    } catch (Exception e) {
        Log.e(TAG, "保存阅读进度失败", e);
    }
}
```

- [ ] **Step 5: 新增 getReadingProgress 方法**

```java
/**
 * 读取阅读进度，无记录返回 -1
 */
public int getReadingProgress(long documentId) {
    try {
        String key = "reading_progress_" + documentId;
        Cursor cursor = database.query(
                LinkDbHelper.TABLE_CONFIG,
                new String[]{LinkDbHelper.COLUMN_CONFIG_VALUE},
                LinkDbHelper.COLUMN_CONFIG_KEY + " = ?",
                new String[]{key},
                null, null, null
        );
        int page = -1;
        if (cursor.moveToFirst()) {
            page = Integer.parseInt(cursor.getString(0));
        }
        cursor.close();
        return page;
    } catch (Exception e) {
        Log.e(TAG, "读取阅读进度失败", e);
        return -1;
    }
}
```

- [ ] **Step 6: 编译确认**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/person/notfresh/readingshare/db/DocumentDao.java
git commit -m "feat: add bookmark CRUD and reading progress methods to DocumentDao"
```

---

### Task 4: 书签菜单项 + 添加/编辑/删除对话框

**Files:**
- Modify: `app/src/main/res/menu/document_viewer_menu.xml`
- Modify: `app/src/main/java/person/notfresh/readingshare/ui/document/DocumentViewerActivity.java`

- [ ] **Step 1: 修改菜单 XML**

在 `document_viewer_menu.xml` 中，在 `action_save_to_documents` 之前添加：

```xml
<item
    android:id="@+id/action_add_bookmark"
    android:title="添加书签"
    android:icon="@android:drawable/ic_menu_compass"
    app:showAsAction="ifRoom" />
```

- [ ] **Step 2: DocumentViewerActivity 增加书签相关字段和逻辑**

在 `isLandscape` 字段之后添加：

```java
private boolean bookmarkExistsForCurrentPage;
```

- [ ] **Step 3: 在 onOptionsItemSelected 中处理书签菜单**

在 `onOptionsItemSelected` 方法中，`if (id == R.id.action_save_to_documents)` 之前添加：

```java
if (id == R.id.action_add_bookmark) {
    showBookmarkDialog();
    return true;
}
```

- [ ] **Step 4: 在 onPrepareOptionsMenu 中更新书签菜单状态**

在 `onPrepareOptionsMenu` 方法中，`saveItem.setVisible(...)` 之后添加：

```java
MenuItem bookmarkItem = menu.findItem(R.id.action_add_bookmark);
if (bookmarkItem != null) {
    // 检查当前页是否已有书签
    if (document != null && document.getId() > 0) {
        List<BookmarkItem> bookmarks = documentDao.getBookmarksByDocument(document.getId());
        bookmarkExistsForCurrentPage = false;
        for (BookmarkItem bm : bookmarks) {
            if (bm.getPageIndex() == currentPageIndex) {
                bookmarkExistsForCurrentPage = true;
                bookmarkItem.setTitle("编辑书签");
                break;
            }
        }
        if (!bookmarkExistsForCurrentPage) {
            bookmarkItem.setTitle("添加书签");
        }
    }
}
```

- [ ] **Step 5: 实现 showBookmarkDialog 方法**

在 `saveToDocuments()` 方法之后添加：

```java
/**
 * 显示书签添加/编辑对话框
 */
private void showBookmarkDialog() {
    if (document == null || document.getId() <= 0) {
        Toast.makeText(this, "请先将文档保存到文档列表", Toast.LENGTH_SHORT).show();
        return;
    }

    // 检查当前页是否已有书签
    List<BookmarkItem> bookmarks = documentDao.getBookmarksByDocument(document.getId());
    BookmarkItem existingBookmark = null;
    for (BookmarkItem bm : bookmarks) {
        if (bm.getPageIndex() == currentPageIndex) {
            existingBookmark = bm;
            break;
        }
    }

    boolean isEdit = existingBookmark != null;

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(isEdit ? "编辑书签" : "添加书签");

    View dialogView = getLayoutInflater().inflate(R.layout.dialog_bookmark, null);
    final EditText etNote = dialogView.findViewById(R.id.et_bookmark_note);
    TextView tvHint = dialogView.findViewById(R.id.tv_bookmark_hint);
    tvHint.setText("当前第 " + (currentPageIndex + 1) + " 页，是否为书签添加备注？");
    if (isEdit && existingBookmark.getNote() != null) {
        etNote.setText(existingBookmark.getNote());
    }
    builder.setView(dialogView);

    builder.setPositiveButton("确定", (dialog, which) -> {
        String note = etNote.getText().toString().trim();
        if (note.isEmpty()) {
            note = null;
        }
        if (isEdit) {
            // 更新备注：先删除旧书签，再添加新书签
            documentDao.deleteBookmark(existingBookmark.getId());
        }
        long result = documentDao.addBookmark(document.getId(), currentPageIndex, note);
        if (result > 0) {
            Toast.makeText(this, isEdit ? "书签已更新" : "已添加书签", Toast.LENGTH_SHORT).show();
            invalidateOptionsMenu();
            // 如果书签侧边栏正在显示，刷新列表
            if (tocAdapter != null) {
                List<BookmarkItem> updated = documentDao.getBookmarksByDocument(document.getId());
                tocAdapter.setBookmarks(updated);
            }
        } else {
            Toast.makeText(this, "添加书签失败", Toast.LENGTH_SHORT).show();
        }
    });

    builder.setNegativeButton("取消", null);

    if (isEdit) {
        builder.setNeutralButton("删除", (dialog, which) -> {
            documentDao.deleteBookmark(existingBookmark.getId());
            Toast.makeText(this, "书签已删除", Toast.LENGTH_SHORT).show();
            invalidateOptionsMenu();
            if (tocAdapter != null) {
                List<BookmarkItem> updated = documentDao.getBookmarksByDocument(document.getId());
                tocAdapter.setBookmarks(updated);
            }
        });
    }

    builder.show();
}
```

- [ ] **Step 6: 创建 dialog_bookmark.xml 布局**

Create `app/src/main/res/layout/dialog_bookmark.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:id="@+id/tv_bookmark_hint"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:paddingBottom="8dp" />

    <EditText
        android:id="@+id/et_bookmark_note"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="输入备注，可选"
        android:inputType="text"
        android:maxLines="3" />
</LinearLayout>
```

- [ ] **Step 7: 在 showPage 中更新菜单以反映书签状态**

在 `showPage()` 方法末尾（`if (tocAdapter != null)` 块之后）添加：

```java
// 刷新菜单以更新书签按钮文字
invalidateOptionsMenu();
```

- [ ] **Step 8: Commit**

```bash
git add app/src/main/res/menu/document_viewer_menu.xml \
        app/src/main/res/layout/dialog_bookmark.xml \
        app/src/main/java/person/notfresh/readingshare/ui/document/DocumentViewerActivity.java
git commit -m "feat: add bookmark menu item and add/edit/delete dialog"
```

---

### Task 5: 书签列表项布局 + TocAdapter 扩展

**Files:**
- Create: `app/src/main/res/layout/item_toc_bookmark.xml`
- Modify: `app/src/main/java/person/notfresh/readingshare/ui/document/TocAdapter.java`

- [ ] **Step 1: 创建 item_toc_bookmark.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:padding="12dp"
    android:background="?android:attr/selectableItemBackground"
    android:gravity="center_vertical">

    <TextView
        android:id="@+id/tv_bookmark_page"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="第 1 页"
        android:textSize="14sp"
        android:textStyle="bold"
        android:layout_marginEnd="12dp"
        android:minWidth="60dp" />

    <TextView
        android:id="@+id/tv_bookmark_note"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:textSize="13sp"
        android:ellipsize="end"
        android:maxLines="2" />
</LinearLayout>
```

- [ ] **Step 2: 修改 TocAdapter — 新增 VIEW_TYPE_BOOKMARK**

在 `VIEW_TYPE_OUTLINE = 1` 之后添加：

```java
private static final int VIEW_TYPE_BOOKMARK = 2;
```

- [ ] **Step 3: 新增书签相关字段和方法**

在 `outlineItems` 字段之后添加：

```java
private List<BookmarkItem> bookmarkItems = new ArrayList<>();
```

新增方法：

```java
public void setBookmarks(List<BookmarkItem> bookmarks) {
    this.bookmarkItems = bookmarks != null ? new ArrayList<>(bookmarks) : new ArrayList<>();
    if (!isPageView && currentViewType == ViewType.BOOKMARK) {
        notifyDataSetChanged();
    }
}
```

- [ ] **Step 4: 重构视图类型枚举**

将 `isPageView` boolean 改为枚举方式，以便支持三种视图：

```java
public enum ViewType { PAGE, OUTLINE, BOOKMARK }
private ViewType currentViewType = ViewType.PAGE;
```

修改 `setPageView` 方法：

```java
public void setPageView(boolean isPageView) {
    if (isPageView) {
        currentViewType = ViewType.PAGE;
    } else {
        currentViewType = ViewType.OUTLINE;
    }
    notifyDataSetChanged();
}

public void setViewType(ViewType viewType) {
    this.currentViewType = viewType;
    notifyDataSetChanged();
}
```

修改 `getItemViewType`：

```java
@Override
public int getItemViewType(int position) {
    switch (currentViewType) {
        case PAGE: return VIEW_TYPE_PAGE;
        case OUTLINE: return VIEW_TYPE_OUTLINE;
        case BOOKMARK: return VIEW_TYPE_BOOKMARK;
        default: return VIEW_TYPE_PAGE;
    }
}
```

- [ ] **Step 5: 修改 onCreateViewHolder 处理书签视图**

在 `else if (viewType == VIEW_TYPE_OUTLINE)` 之后添加：

```java
} else if (viewType == VIEW_TYPE_BOOKMARK) {
    View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_toc_bookmark, parent, false);
    return new BookmarkViewHolder(view);
}
```

- [ ] **Step 6: 修改 onBindViewHolder 处理书签视图**

在 `bindOutlineViewHolder` 的 `else if` 之后添加：

```java
} else if (holder instanceof BookmarkViewHolder) {
    bindBookmarkViewHolder((BookmarkViewHolder) holder, position);
}
```

新增绑定方法：

```java
private void bindBookmarkViewHolder(BookmarkViewHolder holder, int position) {
    if (bookmarkItems.isEmpty()) {
        holder.tvBookmarkPage.setText("暂无书签");
        holder.tvBookmarkNote.setText("");
        holder.itemView.setOnClickListener(null);
        holder.itemView.setClickable(false);
        return;
    }

    BookmarkItem item = bookmarkItems.get(position);
    holder.tvBookmarkPage.setText(String.format(Locale.getDefault(), "第 %d 页", item.getPageNumber()));
    holder.tvBookmarkNote.setText(item.getNote() != null ? item.getNote() : "无备注");
    if (item.getNote() == null) {
        holder.tvBookmarkNote.setTextColor(holder.itemView.getContext().getResources()
                .getColor(android.R.color.darker_gray, null));
    } else {
        holder.tvBookmarkNote.setTextColor(holder.itemView.getContext().getResources()
                .getColor(android.R.color.black, null));
    }

    holder.itemView.setOnClickListener(v -> {
        if (listener != null) {
            listener.onPageClick(item.getPageIndex());
        }
    });

    holder.itemView.setOnLongClickListener(v -> {
        new android.app.AlertDialog.Builder(holder.itemView.getContext())
                .setTitle("删除书签")
                .setMessage("确定要删除书签 \"第 " + item.getPageNumber() + " 页\" 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    if (bookmarkCallback != null) {
                        bookmarkCallback.onBookmarkDelete(item.getId());
                    }
                })
                .setNegativeButton("取消", null)
                .show();
        return true;
    });
}
```

- [ ] **Step 7: 修改 getItemCount 处理书签视图**

在 `getItemCount` 方法的 `else` 块后添加书签处理：

```java
if (currentViewType == ViewType.BOOKMARK) {
    return bookmarkItems.isEmpty() ? 1 : bookmarkItems.size();
}
```

- [ ] **Step 8: 新增书签回调接口和 ViewHolder**

在接口部分添加：

```java
public interface OnBookmarkDeleteCallback {
    void onBookmarkDelete(long bookmarkId);
}

private OnBookmarkDeleteCallback bookmarkCallback;

public void setOnBookmarkDeleteCallback(OnBookmarkDeleteCallback callback) {
    this.bookmarkCallback = callback;
}
```

在文件末尾添加 ViewHolder：

```java
static class BookmarkViewHolder extends RecyclerView.ViewHolder {
    TextView tvBookmarkPage;
    TextView tvBookmarkNote;

    BookmarkViewHolder(@NonNull View itemView) {
        super(itemView);
        tvBookmarkPage = itemView.findViewById(R.id.tv_bookmark_page);
        tvBookmarkNote = itemView.findViewById(R.id.tv_bookmark_note);
    }
}
```

- [ ] **Step 9: Commit**

```bash
git add app/src/main/res/layout/item_toc_bookmark.xml \
        app/src/main/java/person/notfresh/readingshare/ui/document/TocAdapter.java
git commit -m "feat: add bookmark view type to TocAdapter with delete support"
```

---

### Task 6: 侧边栏增加书签页签按钮

**Files:**
- Modify: `app/src/main/res/layout/activity_document_viewer.xml`
- Modify: `app/src/main/java/person/notfresh/readingshare/ui/document/DocumentViewerActivity.java`

- [ ] **Step 1: 修改布局 — 增加第三个按钮**

在 `activity_document_viewer.xml` 的视图切换区域（`btn_view_pages` / `btn_view_outline` 所在 `LinearLayout`）中，将两个按钮改为三个：

```xml
<Button
    android:id="@+id/btn_view_pages"
    android:layout_width="0dp"
    android:layout_height="wrap_content"
    android:layout_weight="1"
    android:text="页码"
    android:textSize="12sp"
    android:layout_marginEnd="2dp"
    style="?android:attr/buttonStyleSmall" />

<Button
    android:id="@+id/btn_view_outline"
    android:layout_width="0dp"
    android:layout_height="wrap_content"
    android:layout_weight="1"
    android:text="目录"
    android:textSize="12sp"
    android:layout_marginStart="2dp"
    android:layout_marginEnd="2dp"
    style="?android:attr/buttonStyleSmall" />

<Button
    android:id="@+id/btn_view_bookmarks"
    android:layout_width="0dp"
    android:layout_height="wrap_content"
    android:layout_weight="1"
    android:text="书签"
    android:textSize="12sp"
    android:layout_marginStart="2dp"
    style="?android:attr/buttonStyleSmall" />
```

- [ ] **Step 2: DocumentViewerActivity 中初始化书签按钮**

在字段声明中添加 `btnViewBookmarks`，在 `findViewById` 部分添加：

```java
btnViewBookmarks = findViewById(R.id.btn_view_bookmarks);
```

- [ ] **Step 3: 设置书签按钮点击事件和 initToc 中加载书签数据**

在 `switchToOutlineView` 方法之后添加：

```java
private void switchToBookmarkView() {
    if (tocAdapter != null) {
        tocAdapter.setViewType(TocAdapter.ViewType.BOOKMARK);
        btnViewPages.setEnabled(true);
        btnViewOutline.setEnabled(true);
        btnViewBookmarks.setEnabled(false);
    }
}
```

修改 `switchToPageView` 方法，在 `btnViewOutline.setEnabled(true)` 之后添加：

```java
btnViewBookmarks.setEnabled(true);
```

修改 `switchToOutlineView` 方法，在 `btnViewPages.setEnabled(true)` 之后添加：

```java
btnViewBookmarks.setEnabled(true);
```

- [ ] **Step 4: initToc 中设置书签回调和加载数据**

在 `initToc()` 方法中，`tocAdapter.setOnPageClickListener(...)` 之后添加：

```java
tocAdapter.setOnBookmarkDeleteCallback(bookmarkId -> {
    documentDao.deleteBookmark(bookmarkId);
    // 刷新书签列表
    List<BookmarkItem> updated = documentDao.getBookmarksByDocument(document.getId());
    tocAdapter.setBookmarks(updated);
    Toast.makeText(DocumentViewerActivity.this, "书签已删除", Toast.LENGTH_SHORT).show();
    invalidateOptionsMenu();
});
```

同时，在 `initToc` 中加载书签数据：

```java
// 加载书签数据
if (document != null && document.getId() > 0) {
    tocAdapter.setBookmarks(documentDao.getBookmarksByDocument(document.getId()));
}
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/activity_document_viewer.xml \
        app/src/main/java/person/notfresh/readingshare/ui/document/DocumentViewerActivity.java
git commit -m "feat: add bookmark tab button to TOC sidebar"
```

---

### Task 7: 自动保存与恢复阅读进度

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/ui/document/DocumentViewerActivity.java`

- [ ] **Step 1: 保存进度 — 在 showPage 中保存**

在 `showPage()` 方法中，`updatePageInfo()` 调用之后添加：

```java
// 保存阅读进度
if (document != null && document.getId() > 0) {
    documentDao.saveReadingProgress(document.getId(), currentPageIndex);
}
```

- [ ] **Step 2: 恢复进度 — 在 loadPdf 中读取上次页码**

修改 `loadPdf()` 方法中的 `showPage` 调用部分。将：

```java
currentPageIndex = 0;
showPage(currentPageIndex);
```

改为：

```java
// 尝试恢复上次阅读进度
int savedPage = -1;
if (document != null && document.getId() > 0) {
    savedPage = documentDao.getReadingProgress(document.getId());
}
if (savedPage > 0 && savedPage < totalPages) {
    currentPageIndex = savedPage;
} else {
    currentPageIndex = 0;
}
showPage(currentPageIndex);
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/person/notfresh/readingshare/ui/document/DocumentViewerActivity.java
git commit -m "feat: save reading progress on page change and restore on open"
```

---

### Task 8: 编译、运行、手动验证

**Files:** 无新增，仅验证

- [ ] **Step 1: 编译确认**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL，无警告

- [ ] **Step 2: 安装到设备/模拟器**

Run: `./gradlew installDebug`

- [ ] **Step 3: 验证清单**

| 测试项 | 预期结果 |
|--------|----------|
| 打开 PDF 后，菜单有"添加书签"按钮 | 菜单显示 compass 图标 |
| 点击"添加书签"，弹出对话框，可输入备注 | 对话框正常弹出 |
| 添加书签后，侧边栏书签页签显示该条目 | 点击跳转正确 |
| 对已有书签的页再次点击"添加书签"，变为"编辑书签" | 可修改备注或删除 |
| 长按书签项弹出删除确认 | 删除后列表刷新 |
| 翻页后退出，重新打开自动跳转到上次页码 | 直接从上次页码开始 |
| 从外部打开未保存的 PDF，添加书签提示"先保存" | Toast 提示 |
