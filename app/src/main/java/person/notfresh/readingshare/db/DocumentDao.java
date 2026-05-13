package person.notfresh.readingshare.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import person.notfresh.readingshare.model.BookmarkItem;
import person.notfresh.readingshare.model.DocumentItem;
import person.notfresh.readingshare.model.DocumentType;

public class DocumentDao {
    private LinkDbHelper dbHelper;
    private SQLiteDatabase database;
    private static final String TAG = "DocumentDao";

    public DocumentDao(Context context) {
        dbHelper = new LinkDbHelper(context);
    }

    public void open() {
        database = dbHelper.getWritableDatabase();
    }

    public void close() {
        dbHelper.close();
    }

    /**
     * 插入文档
     */
    public long insertDocument(DocumentItem item) {
        ContentValues values = new ContentValues();
        values.put(LinkDbHelper.COLUMN_DOC_TITLE, item.getTitle());
        values.put(LinkDbHelper.COLUMN_DOC_FILE_PATH, item.getFilePath());
        values.put(LinkDbHelper.COLUMN_DOC_TYPE, item.getType().name());
        values.put(LinkDbHelper.COLUMN_DOC_TIMESTAMP, item.getTimestamp());
        values.put(LinkDbHelper.COLUMN_DOC_FILE_SIZE, item.getFileSize());
        values.put(LinkDbHelper.COLUMN_DOC_REMARK, item.getRemark());
        values.put(LinkDbHelper.COLUMN_DOC_IS_PINNED, item.isPinned() ? 1 : 0);
        values.put(LinkDbHelper.COLUMN_DOC_CLICK_COUNT, item.getClickCount());

        long docId = database.insert(LinkDbHelper.TABLE_DOCUMENTS, null, values);
        item.setId(docId);
        return docId;
    }

    /**
     * 删除文档（同时删除文件）
     */
    public boolean deleteDocument(long id) {
        Log.d(TAG, "删除文档ID: " + id);
        
        // 获取文档信息以便删除文件
        DocumentItem item = getDocumentById(id);
        if (item != null) {
            // 删除文件
            java.io.File file = new java.io.File(item.getFilePath());
            if (file.exists()) {
                boolean deleted = file.delete();
                Log.d(TAG, "文件删除" + (deleted ? "成功" : "失败") + ": " + item.getFilePath());
            }
        }
        
        // 删除数据库记录
        int deletedRows = database.delete(
                LinkDbHelper.TABLE_DOCUMENTS,
                LinkDbHelper.COLUMN_DOC_ID + " = ?",
                new String[]{String.valueOf(id)}
        );
        
        Log.d(TAG, "数据库删除" + (deletedRows > 0 ? "成功" : "失败"));
        return deletedRows > 0;
    }

    /**
     * 根据ID获取文档
     */
    public DocumentItem getDocumentById(long id) {
        Cursor cursor = database.query(
                LinkDbHelper.TABLE_DOCUMENTS,
                null,
                LinkDbHelper.COLUMN_DOC_ID + " = ?",
                new String[]{String.valueOf(id)},
                null,
                null,
                null
        );

        DocumentItem item = null;
        if (cursor.moveToFirst()) {
            item = cursorToDocumentItem(cursor);
        }
        cursor.close();
        return item;
    }

    /**
     * 获取所有文档
     */
    public List<DocumentItem> getAllDocuments() {
        List<DocumentItem> documents = new ArrayList<>();

        Cursor cursor = database.query(
                LinkDbHelper.TABLE_DOCUMENTS,
                null,
                null,
                null,
                null,
                null,
                LinkDbHelper.COLUMN_DOC_TIMESTAMP + " DESC"
        );

        if (cursor.moveToFirst()) {
            do {
                DocumentItem item = cursorToDocumentItem(cursor);
                documents.add(item);
            } while (cursor.moveToNext());
        }
        cursor.close();

        return documents;
    }

    /**
     * 获取按日期分组的文档
     */
    public Map<String, List<DocumentItem>> getDocumentsGroupByDate() {
        Map<String, List<DocumentItem>> groupedDocuments = new TreeMap<>(Collections.reverseOrder());
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        List<DocumentItem> allDocuments = getAllDocuments();
        for (DocumentItem doc : allDocuments) {
            String date = dateFormat.format(new Date(doc.getTimestamp()));
            groupedDocuments.computeIfAbsent(date, k -> new ArrayList<>()).add(doc);
        }

        return groupedDocuments;
    }

    /**
     * 获取置顶文档
     */
    public List<DocumentItem> getPinnedDocuments() {
        List<DocumentItem> pinnedDocuments = new ArrayList<>();

        Cursor cursor = database.query(
                LinkDbHelper.TABLE_DOCUMENTS,
                null,
                LinkDbHelper.COLUMN_DOC_IS_PINNED + " = 1",
                null,
                null,
                null,
                LinkDbHelper.COLUMN_DOC_TIMESTAMP + " DESC"
        );

        if (cursor.moveToFirst()) {
            do {
                DocumentItem item = cursorToDocumentItem(cursor);
                pinnedDocuments.add(item);
            } while (cursor.moveToNext());
        }
        cursor.close();

        return pinnedDocuments;
    }

    /**
     * 切换置顶状态
     */
    public void togglePinStatus(long id) {
        DocumentItem item = getDocumentById(id);
        if (item != null) {
            ContentValues values = new ContentValues();
            values.put(LinkDbHelper.COLUMN_DOC_IS_PINNED, item.isPinned() ? 0 : 1);
            database.update(
                    LinkDbHelper.TABLE_DOCUMENTS,
                    values,
                    LinkDbHelper.COLUMN_DOC_ID + " = ?",
                    new String[]{String.valueOf(id)}
            );
        }
    }

    /**
     * 更新文档标题
     */
    public void updateDocumentTitle(long id, String newTitle) {
        ContentValues values = new ContentValues();
        values.put(LinkDbHelper.COLUMN_DOC_TITLE, newTitle);
        database.update(
                LinkDbHelper.TABLE_DOCUMENTS,
                values,
                LinkDbHelper.COLUMN_DOC_ID + " = ?",
                new String[]{String.valueOf(id)}
        );
    }

    /**
     * 更新备注
     */
    public void updateDocumentRemark(long id, String remark) {
        ContentValues values = new ContentValues();
        values.put(LinkDbHelper.COLUMN_DOC_REMARK, remark);
        database.update(
                LinkDbHelper.TABLE_DOCUMENTS,
                values,
                LinkDbHelper.COLUMN_DOC_ID + " = ?",
                new String[]{String.valueOf(id)}
        );
    }

    /**
     * 更新打开次数
     */
    public void updateClickCount(long id, int count) {
        ContentValues values = new ContentValues();
        values.put(LinkDbHelper.COLUMN_DOC_CLICK_COUNT, count);
        database.update(
                LinkDbHelper.TABLE_DOCUMENTS,
                values,
                LinkDbHelper.COLUMN_DOC_ID + " = ?",
                new String[]{String.valueOf(id)}
        );
    }

    /**
     * 搜索文档（标题、类型）
     */
    public List<DocumentItem> searchDocuments(String query) {
        List<DocumentItem> documents = new ArrayList<>();
        String searchQuery = "%" + query + "%";

        Cursor cursor = database.query(
                LinkDbHelper.TABLE_DOCUMENTS,
                null,
                LinkDbHelper.COLUMN_DOC_TITLE + " LIKE ? OR " + LinkDbHelper.COLUMN_DOC_TYPE + " LIKE ?",
                new String[]{searchQuery, searchQuery},
                null,
                null,
                LinkDbHelper.COLUMN_DOC_TIMESTAMP + " DESC"
        );

        if (cursor.moveToFirst()) {
            do {
                DocumentItem item = cursorToDocumentItem(cursor);
                documents.add(item);
            } while (cursor.moveToNext());
        }
        cursor.close();

        return documents;
    }

    /**
     * 从游标创建 DocumentItem 对象
     */
    private DocumentItem cursorToDocumentItem(Cursor cursor) {
        long id = cursor.getLong(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_DOC_ID));
        String title = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_DOC_TITLE));
        String filePath = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_DOC_FILE_PATH));
        String typeStr = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_DOC_TYPE));
        long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_DOC_TIMESTAMP));
        long fileSize = cursor.getLong(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_DOC_FILE_SIZE));
        String remark = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_DOC_REMARK));
        boolean isPinned = cursor.getInt(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_DOC_IS_PINNED)) == 1;
        int clickCount = cursor.getInt(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_DOC_CLICK_COUNT));

        DocumentType type = DocumentType.fromString(typeStr);
        DocumentItem item = new DocumentItem(title, filePath, type, timestamp);
        item.setId(id);
        item.setFileSize(fileSize);
        item.setRemark(remark);
        item.setPinned(isPinned);
        item.setClickCount(clickCount);

        return item;
    }

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
}

