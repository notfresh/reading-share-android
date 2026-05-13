# 文档书签与阅读进度保存设计

## 概述

为 PDF 阅读器增加两个功能：
1. **页码书签** — 可在阅读时添加/删除书签，书签支持备注文字，TOC 侧边栏增加书签页签查看
2. **自动保存进度** — 每次翻页实时保存当前页码，下次打开时自动跳转到上次阅读位置

## 数据层设计

### 1. 新建 `document_bookmarks` 表

数据库版本 12 → 13，在 `LinkDbHelper` 中新增：

```sql
CREATE TABLE document_bookmarks (
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    document_id INTEGER NOT NULL,
    page_index INTEGER NOT NULL,
    note TEXT,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (document_id) REFERENCES documents(_id)
);
```

新增常量：
```java
public static final String TABLE_BOOKMARKS = "document_bookmarks";
public static final String COLUMN_BOOKMARK_ID = "_id";
public static final String COLUMN_BOOKMARK_DOC_ID = "document_id";
public static final String COLUMN_BOOKMARK_PAGE_INDEX = "page_index";
public static final String COLUMN_BOOKMARK_NOTE = "note";
public static final String COLUMN_BOOKMARK_CREATED_AT = "created_at";
```

升级逻辑（`onUpgrade`，oldVersion < 13）：
```java
if (oldVersion < 13) {
    db.execSQL(SQL_CREATE_BOOKMARKS);
}
```

注意：升级逻辑放在 `oldVersion < 10` 的 `db.execSQL` 分支之前（避免大版本号跳跃走 `oldVersion < 8` 的重建路径）。

### 2. `DocumentDao` 新增方法

| 方法签名 | 说明 |
|----------|------|
| `addBookmark(long documentId, int pageIndex, String note)` | 插入书签，返回 bookmarkId |
| `deleteBookmark(long bookmarkId)` | 删除指定书签 |
| `getBookmarksByDocument(long documentId)` | 按文档查询书签，按 created_at 升序，返回 `List<BookmarkItem>` |
| `saveReadingProgress(long documentId, int pageIndex)` | 将 `reading_progress_<documentId>` 存入 config 表 |
| `getReadingProgress(long documentId)` | 从 config 表读取上次页码，无记录返回 -1 |

### 3. 新增模型类 `BookmarkItem`

```java
public class BookmarkItem {
    private long id;
    private long documentId;
    private int pageIndex;
    private String note;
    private long createdAt;

    // 标准 getter/setter
}
```

文件位置：`app/src/main/java/person/notfresh/readingshare/model/BookmarkItem.java`

## UI 层设计

### 1. 添加书签菜单

在 `document_viewer_menu.xml` 新增菜单项（放在保存按钮之前）：

```xml
<item
    android:id="@+id/action_add_bookmark"
    android:title="添加书签"
    android:icon="@android:drawable/ic_menu_compass"
    app:showAsAction="ifRoom" />
```

点击行为：
- 弹出 `AlertDialog`，标题"添加书签"，正文"当前第 X 页，是否为书签添加备注？"
- 包含 `EditText`（hint="输入备注，可选"）
- 确定按钮：保存到数据库，Toast "已添加书签"
- 取消按钮：关闭
- 如果该页已有书签，标题变为"编辑书签"，EditText 填入已有备注，增加"删除"按钮

### 2. TOC 侧边栏增加书签页签

**布局调整**：将现有 `btnViewPages` / `btnViewOutline` 改为三个按钮水平排列：`页码` / `目录` / `书签`。

**TocAdapter 变更**：

新增视图类型：
- `VIEW_TYPE_PAGE = 0`（保持不变）
- `VIEW_TYPE_OUTLINE = 1`（保持不变）
- `VIEW_TYPE_BOOKMARK = 2`（新增）

新增方法：
- `setBookmarks(List<BookmarkItem> bookmarks)` — 设置书签数据并刷新
- `switchToBookmarkView()` — 切换至书签视图

新增 ViewHolder 和布局：
- `BookmarkViewHolder` — 绑定 `item_toc_bookmark.xml`
- `item_toc_bookmark.xml` — 左侧显示"第 X 页"，右侧显示备注文字（无备注时显示灰色占位符）
- 点击跳转对应页码，关闭侧边栏
- 长按弹出删除确认对话框

### 3. 自动保存与恢复进度

**保存**：在 `DocumentViewerActivity.showPage()` 中，更新页码后同步调用 `documentDao.saveReadingProgress(documentId, pageIndex)`。此调用与 `updatePageInfo()`、`tocAdapter.setCurrentPage()` 在同一流程中，数据库写入耗时约 1-5ms，不会造成可感知卡顿。

**恢复**：在 `loadPdf()` 成功后（`showPage()` 之前），调用 `documentDao.getReadingProgress(documentId)` 读取上次页码，如果有效（> 0 且 < totalPages），则 `currentPageIndex = savedPage`，直接 `showPage(currentPageIndex)` 跳过第 0 页。

## 文件变更清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `LinkDbHelper.java` | 修改 | 新增常量、表定义、升级逻辑 |
| `DocumentDao.java` | 修改 | 新增书签和进度相关方法 |
| `BookmarkItem.java` | 新增 | 书签数据模型 |
| `DocumentViewerActivity.java` | 修改 | 菜单处理、书签对话框、TOC 页签切换、进度保存/恢复 |
| `TocAdapter.java` | 修改 | 新增 VIEW_TYPE_BOOKMARK 视图类型 |
| `item_toc_bookmark.xml` | 新增 | 书签列表项布局 |
| `document_viewer_menu.xml` | 修改 | 新增书签菜单项 |
| `activity_document_viewer.xml` | 修改 | 增加书签切换按钮 |

## 边界情况

当 `documentId == -1`（从外部打开且尚未保存到数据库）时，书签和进度保存跳过数据库操作，仅 Log.w 记录。用户需先"保存到文档"后，才能使用书签和进度功能。

## 错误处理

- 书签添加失败（数据库错误）：Toast 提示"添加书签失败"
- 进度保存失败：静默处理，仅 Log.e 记录，不影响阅读
- PDF 文件不存在/已损坏：保持现有逻辑，Toast + finish()
