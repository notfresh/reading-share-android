package person.notfresh.readingshare.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import person.notfresh.readingshare.core.model.Subject;
import person.notfresh.readingshare.core.model.SubjectItem;
import person.notfresh.readingshare.core.model.SubjectUtil;

/**
 * 主题数据访问层
 * 提供主题和主题项的数据库操作
 */
public class SubjectDao {
    private LinkDbHelper dbHelper;
    private SQLiteDatabase database;
    private LinkDao linkDao;  // 用于检查 LinkItem 是否存在
    private static final String TAG = "SubjectDao";

    public SubjectDao(Context context) {
        dbHelper = new LinkDbHelper(context);
        linkDao = new LinkDao(context);
    }

    public void open() {
        database = dbHelper.getWritableDatabase();
        linkDao.open();
    }

    public void close() {
        linkDao.close();
        dbHelper.close();
    }

    // ==================== Subject CRUD ====================

    /**
     * 插入主题
     */
    public long insertSubject(Subject subject) {
        ContentValues values = new ContentValues();
        values.put(LinkDbHelper.COLUMN_SUBJECT_TITLE, subject.getTitle());
        values.put(LinkDbHelper.COLUMN_SUBJECT_DESCRIBE, subject.getDescribe());
        values.put(LinkDbHelper.COLUMN_SUBJECT_CREATE_TIME, subject.getCreateTime());

        long subjectId = database.insert(LinkDbHelper.TABLE_SUBJECTS, null, values);
        subject.setId(subjectId);
        return subjectId;
    }

    /**
     * 更新主题
     */
    public boolean updateSubject(Subject subject) {
        ContentValues values = new ContentValues();
        values.put(LinkDbHelper.COLUMN_SUBJECT_TITLE, subject.getTitle());
        values.put(LinkDbHelper.COLUMN_SUBJECT_DESCRIBE, subject.getDescribe());

        int rowsAffected = database.update(
                LinkDbHelper.TABLE_SUBJECTS,
                values,
                LinkDbHelper.COLUMN_SUBJECT_ID + " = ?",
                new String[]{String.valueOf(subject.getId())}
        );
        return rowsAffected > 0;
    }

    /**
     * 删除主题（级联删除所有主题项和图片文件）
     */
    public boolean deleteSubject(long subjectId) {
        Log.d(TAG, "删除主题ID: " + subjectId);

        database.beginTransaction();
        try {
            // 1. 获取所有主题项的图片路径
            List<String> imagePaths = getSubjectImagePaths(subjectId);

            // 2. 删除所有主题项的图片记录
            database.delete(
                    LinkDbHelper.TABLE_SUBJECT_ITEM_IMAGES,
                    LinkDbHelper.COLUMN_SUBJECT_ITEM_IMAGE_ITEM_ID + " IN (" +
                            "SELECT " + LinkDbHelper.COLUMN_SUBJECT_ITEM_ID +
                            " FROM " + LinkDbHelper.TABLE_SUBJECT_ITEMS +
                            " WHERE " + LinkDbHelper.COLUMN_SUBJECT_ITEM_SUBJECT_ID + " = ?)",
                    new String[]{String.valueOf(subjectId)}
            );

            // 3. 删除所有主题项
            database.delete(
                    LinkDbHelper.TABLE_SUBJECT_ITEMS,
                    LinkDbHelper.COLUMN_SUBJECT_ITEM_SUBJECT_ID + " = ?",
                    new String[]{String.valueOf(subjectId)}
            );

            // 4. 删除主题
            int rowsAffected = database.delete(
                    LinkDbHelper.TABLE_SUBJECTS,
                    LinkDbHelper.COLUMN_SUBJECT_ID + " = ?",
                    new String[]{String.valueOf(subjectId)}
            );

            // 5. 删除图片文件
            for (String imagePath : imagePaths) {
                File imageFile = new File(imagePath);
                if (imageFile.exists()) {
                    boolean deleted = imageFile.delete();
                    Log.d(TAG, "图片文件删除" + (deleted ? "成功" : "失败") + ": " + imagePath);
                }
            }

            database.setTransactionSuccessful();
            return rowsAffected > 0;
        } finally {
            database.endTransaction();
        }
    }

    /**
     * 根据ID获取主题
     */
    public Subject getSubjectById(long subjectId) {
        Cursor cursor = database.query(
                LinkDbHelper.TABLE_SUBJECTS,
                null,
                LinkDbHelper.COLUMN_SUBJECT_ID + " = ?",
                new String[]{String.valueOf(subjectId)},
                null,
                null,
                null
        );

        Subject subject = null;
        if (cursor.moveToFirst()) {
            subject = cursorToSubject(cursor);
            // 加载主题项
            subject.setSubItems(getSubjectItemsBySubjectId(subjectId));
        }
        cursor.close();
        return subject;
    }

    /**
     * 获取所有主题（按创建时间排序）
     */
    public List<Subject> getAllSubjects() {
        List<Subject> subjects = new ArrayList<>();

        Cursor cursor = database.query(
                LinkDbHelper.TABLE_SUBJECTS,
                null,
                null,
                null,
                null,
                null,
                LinkDbHelper.COLUMN_SUBJECT_CREATE_TIME + " DESC"
        );

        if (cursor.moveToFirst()) {
            do {
                Subject subject = cursorToSubject(cursor);
                subjects.add(subject);
            } while (cursor.moveToNext());
        }
        cursor.close();

        return subjects;
    }

    // ==================== SubjectItem CRUD ====================

    /**
     * 插入主题项
     */
    public long insertSubjectItem(SubjectItem item) {
        // 验证完整性
        if (!SubjectUtil.validateSubjectItem(item)) {
            Log.e(TAG, "SubjectItem 验证失败：必须至少包含 linkId、remark、images 中的一项");
            return -1;
        }

        // 验证图片数量
        if (!SubjectUtil.validateImageCount(item)) {
            Log.e(TAG, "SubjectItem 图片数量超过限制");
            return -1;
        }

        database.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put(LinkDbHelper.COLUMN_SUBJECT_ITEM_SUBJECT_ID, item.getSubjectId());
            values.put(LinkDbHelper.COLUMN_SUBJECT_ITEM_LINK_ID, item.getLinkId());
            values.put(LinkDbHelper.COLUMN_SUBJECT_ITEM_IS_LINK_DELETED, item.isLinkDeleted() ? 1 : 0);
            values.put(LinkDbHelper.COLUMN_SUBJECT_ITEM_REMARK, item.getRemark());
            values.put(LinkDbHelper.COLUMN_SUBJECT_ITEM_ADD_TIME, item.getAddTime());
            values.put(LinkDbHelper.COLUMN_SUBJECT_ITEM_ORDER_INDEX, item.getOrderIndex());
            values.put(LinkDbHelper.COLUMN_SUBJECT_ITEM_IS_ARCHIVED, item.isArchived() ? 1 : 0);
            values.put(LinkDbHelper.COLUMN_SUBJECT_ITEM_ARCHIVED_AT, item.getArchivedAt());

            long itemId = database.insert(LinkDbHelper.TABLE_SUBJECT_ITEMS, null, values);
            item.setId(itemId);

            // 插入图片路径
            if (item.getImages() != null && !item.getImages().isEmpty()) {
                for (String imagePath : item.getImages()) {
                    insertSubjectItemImage(itemId, imagePath);
                }
            }

            database.setTransactionSuccessful();
            return itemId;
        } finally {
            database.endTransaction();
        }
    }

    /**
     * 更新主题项
     */
    public boolean updateSubjectItem(SubjectItem item) {
        // 验证完整性
        if (!SubjectUtil.validateSubjectItem(item)) {
            Log.e(TAG, "SubjectItem 验证失败：必须至少包含 linkId、remark、images 中的一项");
            return false;
        }

        // 验证图片数量
        if (!SubjectUtil.validateImageCount(item)) {
            Log.e(TAG, "SubjectItem 图片数量超过限制");
            return false;
        }

        database.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put(LinkDbHelper.COLUMN_SUBJECT_ITEM_LINK_ID, item.getLinkId());
            values.put(LinkDbHelper.COLUMN_SUBJECT_ITEM_IS_LINK_DELETED, item.isLinkDeleted() ? 1 : 0);
            values.put(LinkDbHelper.COLUMN_SUBJECT_ITEM_REMARK, item.getRemark());
            values.put(LinkDbHelper.COLUMN_SUBJECT_ITEM_ORDER_INDEX, item.getOrderIndex());
            values.put(LinkDbHelper.COLUMN_SUBJECT_ITEM_IS_ARCHIVED, item.isArchived() ? 1 : 0);
            values.put(LinkDbHelper.COLUMN_SUBJECT_ITEM_ARCHIVED_AT, item.getArchivedAt());

            int rowsAffected = database.update(
                    LinkDbHelper.TABLE_SUBJECT_ITEMS,
                    values,
                    LinkDbHelper.COLUMN_SUBJECT_ITEM_ID + " = ?",
                    new String[]{String.valueOf(item.getId())}
            );

            // 更新图片：先删除旧的，再插入新的
            deleteSubjectItemImages(item.getId());
            if (item.getImages() != null && !item.getImages().isEmpty()) {
                for (String imagePath : item.getImages()) {
                    insertSubjectItemImage(item.getId(), imagePath);
                }
            }

            database.setTransactionSuccessful();
            return rowsAffected > 0;
        } finally {
            database.endTransaction();
        }
    }

    /**
     * 删除主题项（同时删除图片文件和图片记录）
     */
    public boolean deleteSubjectItem(long itemId) {
        Log.d(TAG, "删除主题项ID: " + itemId);

        database.beginTransaction();
        try {
            // 1. 获取图片路径
            List<String> imagePaths = getSubjectItemImagePaths(itemId);

            // 2. 删除图片记录
            deleteSubjectItemImages(itemId);

            // 3. 删除主题项
            int rowsAffected = database.delete(
                    LinkDbHelper.TABLE_SUBJECT_ITEMS,
                    LinkDbHelper.COLUMN_SUBJECT_ITEM_ID + " = ?",
                    new String[]{String.valueOf(itemId)}
            );

            // 4. 删除图片文件
            for (String imagePath : imagePaths) {
                File imageFile = new File(imagePath);
                if (imageFile.exists()) {
                    boolean deleted = imageFile.delete();
                    Log.d(TAG, "图片文件删除" + (deleted ? "成功" : "失败") + ": " + imagePath);
                }
            }

            database.setTransactionSuccessful();
            return rowsAffected > 0;
        } finally {
            database.endTransaction();
        }
    }

    /**
     * 根据ID获取主题项
     */
    public SubjectItem getSubjectItemById(long itemId) {
        Cursor cursor = database.query(
                LinkDbHelper.TABLE_SUBJECT_ITEMS,
                null,
                LinkDbHelper.COLUMN_SUBJECT_ITEM_ID + " = ?",
                new String[]{String.valueOf(itemId)},
                null,
                null,
                null
        );

        SubjectItem item = null;
        if (cursor.moveToFirst()) {
            item = cursorToSubjectItem(cursor);
            // 加载图片路径
            item.setImages(getSubjectItemImages(itemId));
        }
        cursor.close();
        return item;
    }

    /**
     * 根据主题ID获取所有主题项（按 orderIndex 排序）
     */
    public List<SubjectItem> getSubjectItemsBySubjectId(long subjectId) {
        List<SubjectItem> items = new ArrayList<>();

        Cursor cursor = database.query(
                LinkDbHelper.TABLE_SUBJECT_ITEMS,
                null,
            LinkDbHelper.COLUMN_SUBJECT_ITEM_SUBJECT_ID + " = ? AND " +
                LinkDbHelper.COLUMN_SUBJECT_ITEM_IS_ARCHIVED + " = 0",
                new String[]{String.valueOf(subjectId)},
                null,
                null,
                LinkDbHelper.COLUMN_SUBJECT_ITEM_ORDER_INDEX + " ASC"
        );

        if (cursor.moveToFirst()) {
            do {
                SubjectItem item = cursorToSubjectItem(cursor);
                // 加载图片路径
                item.setImages(getSubjectItemImages(item.getId()));
                items.add(item);
            } while (cursor.moveToNext());
        }
        cursor.close();

        return items;
    }

    /**
     * 根据主题ID获取归档的主题项（按 archivedAt 排序）
     */
    public List<SubjectItem> getArchivedSubjectItemsBySubjectId(long subjectId, boolean ascending) {
        List<SubjectItem> items = new ArrayList<>();

        String orderBy = LinkDbHelper.COLUMN_SUBJECT_ITEM_ARCHIVED_AT + (ascending ? " ASC" : " DESC");

        Cursor cursor = database.query(
                LinkDbHelper.TABLE_SUBJECT_ITEMS,
                null,
                LinkDbHelper.COLUMN_SUBJECT_ITEM_SUBJECT_ID + " = ? AND " +
                        LinkDbHelper.COLUMN_SUBJECT_ITEM_IS_ARCHIVED + " = 1",
                new String[]{String.valueOf(subjectId)},
                null,
                null,
                orderBy
        );

        if (cursor.moveToFirst()) {
            do {
                SubjectItem item = cursorToSubjectItem(cursor);
                item.setImages(getSubjectItemImages(item.getId()));
                items.add(item);
            } while (cursor.moveToNext());
        }
        cursor.close();

        return items;
    }

    /**
     * 归档主题项
     */
    public boolean archiveSubjectItem(long itemId, long archivedAt) {
        ContentValues values = new ContentValues();
        values.put(LinkDbHelper.COLUMN_SUBJECT_ITEM_IS_ARCHIVED, 1);
        values.put(LinkDbHelper.COLUMN_SUBJECT_ITEM_ARCHIVED_AT, archivedAt);

        int rowsAffected = database.update(
                LinkDbHelper.TABLE_SUBJECT_ITEMS,
                values,
                LinkDbHelper.COLUMN_SUBJECT_ITEM_ID + " = ?",
                new String[]{String.valueOf(itemId)}
        );
        return rowsAffected > 0;
    }

    /**
     * 还原主题项
     */
    public boolean restoreSubjectItem(long itemId) {
        ContentValues values = new ContentValues();
        values.put(LinkDbHelper.COLUMN_SUBJECT_ITEM_IS_ARCHIVED, 0);
        values.put(LinkDbHelper.COLUMN_SUBJECT_ITEM_ARCHIVED_AT, 0);

        int rowsAffected = database.update(
                LinkDbHelper.TABLE_SUBJECT_ITEMS,
                values,
                LinkDbHelper.COLUMN_SUBJECT_ITEM_ID + " = ?",
                new String[]{String.valueOf(itemId)}
        );
        return rowsAffected > 0;
    }

    /**
     * 批量插入主题项（用于从主页/标签页添加链接到主题）
     */
    public void batchInsertSubjectItems(List<SubjectItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        database.beginTransaction();
        try {
            for (SubjectItem item : items) {
                insertSubjectItem(item);
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    // ==================== 图片管理 ====================

    /**
     * 插入主题项图片路径
     */
    private long insertSubjectItemImage(long itemId, String imagePath) {
        ContentValues values = new ContentValues();
        values.put(LinkDbHelper.COLUMN_SUBJECT_ITEM_IMAGE_ITEM_ID, itemId);
        values.put(LinkDbHelper.COLUMN_SUBJECT_ITEM_IMAGE_PATH, imagePath);
        return database.insert(LinkDbHelper.TABLE_SUBJECT_ITEM_IMAGES, null, values);
    }

    /**
     * 获取主题项的所有图片路径
     */
    private List<String> getSubjectItemImages(long itemId) {
        List<String> images = new ArrayList<>();

        Cursor cursor = database.query(
                LinkDbHelper.TABLE_SUBJECT_ITEM_IMAGES,
                new String[]{LinkDbHelper.COLUMN_SUBJECT_ITEM_IMAGE_PATH},
                LinkDbHelper.COLUMN_SUBJECT_ITEM_IMAGE_ITEM_ID + " = ?",
                new String[]{String.valueOf(itemId)},
                null,
                null,
                LinkDbHelper.COLUMN_SUBJECT_ITEM_IMAGE_ID + " ASC"
        );

        if (cursor.moveToFirst()) {
            do {
                String imagePath = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_SUBJECT_ITEM_IMAGE_PATH));
                images.add(imagePath);
            } while (cursor.moveToNext());
        }
        cursor.close();

        return images;
    }

    /**
     * 删除主题项的所有图片记录
     */
    private void deleteSubjectItemImages(long itemId) {
        database.delete(
                LinkDbHelper.TABLE_SUBJECT_ITEM_IMAGES,
                LinkDbHelper.COLUMN_SUBJECT_ITEM_IMAGE_ITEM_ID + " = ?",
                new String[]{String.valueOf(itemId)}
        );
    }

    /**
     * 获取主题的所有图片路径（用于删除主题时）
     */
    private List<String> getSubjectImagePaths(long subjectId) {
        List<String> imagePaths = new ArrayList<>();

        String query = "SELECT " + LinkDbHelper.COLUMN_SUBJECT_ITEM_IMAGE_PATH +
                " FROM " + LinkDbHelper.TABLE_SUBJECT_ITEM_IMAGES +
                " WHERE " + LinkDbHelper.COLUMN_SUBJECT_ITEM_IMAGE_ITEM_ID +
                " IN (" +
                " SELECT " + LinkDbHelper.COLUMN_SUBJECT_ITEM_ID +
                " FROM " + LinkDbHelper.TABLE_SUBJECT_ITEMS +
                " WHERE " + LinkDbHelper.COLUMN_SUBJECT_ITEM_SUBJECT_ID + " = ?)";

        Cursor cursor = database.rawQuery(query, new String[]{String.valueOf(subjectId)});
        if (cursor.moveToFirst()) {
            do {
                String imagePath = cursor.getString(0);
                imagePaths.add(imagePath);
            } while (cursor.moveToNext());
        }
        cursor.close();

        return imagePaths;
    }

    /**
     * 获取主题项的所有图片路径（用于删除主题项时）
     */
    private List<String> getSubjectItemImagePaths(long itemId) {
        return getSubjectItemImages(itemId);
    }

    // ==================== LinkItem 交互 ====================

    /**
     * 检查 LinkItem 是否存在
     */
    public boolean isLinkItemExists(long linkId) {
        if (linkId <= 0) {
            return false;
        }
        // 直接查询数据库检查
        Cursor cursor = database.query(
                LinkDbHelper.TABLE_LINKS,
                new String[]{LinkDbHelper.COLUMN_ID},
                LinkDbHelper.COLUMN_ID + " = ?",
                new String[]{String.valueOf(linkId)},
                null,
                null,
                null,
                "1"
        );
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        return exists;
    }

    /**
     * 检查并更新 SubjectItem 的 isLinkDeleted 标记
     */
    public void checkAndUpdateLinkDeletedStatus(SubjectItem item) {
        if (item.getLinkId() == null || item.getLinkId() <= 0) {
            return;
        }

        boolean exists = isLinkItemExists(item.getLinkId());
        if (!exists && !item.isLinkDeleted()) {
            // LinkItem 不存在，更新标记
            item.setLinkDeleted(true);
            updateSubjectItemLinkDeletedStatus(item.getId(), true);
        }
    }

    /**
     * 更新主题项的 LinkItem 删除标记
     */
    private void updateSubjectItemLinkDeletedStatus(long itemId, boolean isDeleted) {
        ContentValues values = new ContentValues();
        values.put(LinkDbHelper.COLUMN_SUBJECT_ITEM_IS_LINK_DELETED, isDeleted ? 1 : 0);
        database.update(
                LinkDbHelper.TABLE_SUBJECT_ITEMS,
                values,
                LinkDbHelper.COLUMN_SUBJECT_ITEM_ID + " = ?",
                new String[]{String.valueOf(itemId)}
        );
    }

    /**
     * 根据 LinkId 查询所有包含该链接的主题
     */
    public List<Subject> getSubjectsByLinkId(long linkId) {
        List<Subject> subjects = new ArrayList<>();

        String query = "SELECT DISTINCT s.* FROM " + LinkDbHelper.TABLE_SUBJECTS + " s " +
                "JOIN " + LinkDbHelper.TABLE_SUBJECT_ITEMS + " si " +
                "ON s." + LinkDbHelper.COLUMN_SUBJECT_ID + " = si." + LinkDbHelper.COLUMN_SUBJECT_ITEM_SUBJECT_ID + " " +
                "WHERE si." + LinkDbHelper.COLUMN_SUBJECT_ITEM_LINK_ID + " = ? " +
                "ORDER BY s." + LinkDbHelper.COLUMN_SUBJECT_CREATE_TIME + " DESC";

        Cursor cursor = database.rawQuery(query, new String[]{String.valueOf(linkId)});
        if (cursor.moveToFirst()) {
            do {
                Subject subject = cursorToSubject(cursor);
                subjects.add(subject);
            } while (cursor.moveToNext());
        }
        cursor.close();

        return subjects;
    }

    // ==================== 辅助方法 ====================

    /**
     * 从 Cursor 创建 Subject 对象
     */
    private Subject cursorToSubject(Cursor cursor) {
        Subject subject = new Subject();
        subject.setId(cursor.getLong(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_SUBJECT_ID)));
        subject.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_SUBJECT_TITLE)));
        subject.setDescribe(cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_SUBJECT_DESCRIBE)));
        subject.setCreateTime(cursor.getLong(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_SUBJECT_CREATE_TIME)));
        return subject;
    }

    /**
     * 从 Cursor 创建 SubjectItem 对象
     */
    private SubjectItem cursorToSubjectItem(Cursor cursor) {
        SubjectItem item = new SubjectItem();
        item.setId(cursor.getLong(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_SUBJECT_ITEM_ID)));
        item.setSubjectId(cursor.getLong(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_SUBJECT_ITEM_SUBJECT_ID)));

        int linkIdIndex = cursor.getColumnIndex(LinkDbHelper.COLUMN_SUBJECT_ITEM_LINK_ID);
        if (!cursor.isNull(linkIdIndex)) {
            item.setLinkId(cursor.getLong(linkIdIndex));
        }

        item.setLinkDeleted(cursor.getInt(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_SUBJECT_ITEM_IS_LINK_DELETED)) == 1);

        int remarkIndex = cursor.getColumnIndex(LinkDbHelper.COLUMN_SUBJECT_ITEM_REMARK);
        if (!cursor.isNull(remarkIndex)) {
            item.setRemark(cursor.getString(remarkIndex));
        }

        item.setAddTime(cursor.getLong(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_SUBJECT_ITEM_ADD_TIME)));
        item.setOrderIndex(cursor.getInt(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_SUBJECT_ITEM_ORDER_INDEX)));
        item.setArchived(cursor.getInt(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_SUBJECT_ITEM_IS_ARCHIVED)) == 1);
        item.setArchivedAt(cursor.getLong(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_SUBJECT_ITEM_ARCHIVED_AT)));
        return item;
    }
}

