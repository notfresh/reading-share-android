package person.notfresh.readingshare.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.Arrays;
import java.util.HashMap;

import person.notfresh.readingshare.model.LinkItem;

public class LinkDao {
    private LinkDbHelper dbHelper;
    private SQLiteDatabase database;
    private static final String TAG = "LinkDao";

    public LinkDao(Context context) {
        dbHelper = new LinkDbHelper(context);
    }

    public void open() {
        database = dbHelper.getWritableDatabase();
    }

    public void close() {
        dbHelper.close();
    }

    public long insertLink(LinkItem item) {
        ContentValues values = new ContentValues();
        values.put(LinkDbHelper.COLUMN_TITLE, item.getTitle());
        values.put(LinkDbHelper.COLUMN_URL, item.getUrl());
        values.put(LinkDbHelper.COLUMN_SOURCE_APP, item.getSourceApp());
        values.put(LinkDbHelper.COLUMN_TIMESTAMP, item.getTimestamp());
        values.put(LinkDbHelper.COLUMN_ORIGINAL_INTENT, item.getOriginalIntent());
        values.put(LinkDbHelper.COLUMN_TARGET_ACTIVITY, item.getTargetActivity());
        values.put(LinkDbHelper.COLUMN_REMARK, item.getRemark());
        values.put(LinkDbHelper.COLUMN_SUMMARY, item.getSummary());

        long linkId = database.insert(LinkDbHelper.TABLE_LINKS, null, values);
        item.setId(linkId);
        updateLinkTags(item);
        return linkId;
    }

    public void deleteLink(String url) {
        database.delete(
                LinkDbHelper.TABLE_LINKS,
                LinkDbHelper.COLUMN_URL + " = ?",
                new String[]{url}
        );
    }

    public void deleteLink(long id) {
        database.delete(
                LinkDbHelper.TABLE_LINKS,
                LinkDbHelper.COLUMN_ID + " = ?",
                new String[]{String.valueOf(id)}
        );
    }

    public void updateLinkTitle(String url, String newTitle) {
        ContentValues values = new ContentValues();
        values.put(LinkDbHelper.COLUMN_TITLE, newTitle);

        database.update(
                LinkDbHelper.TABLE_LINKS,
                values,
                LinkDbHelper.COLUMN_URL + " = ?",
                new String[]{url}
        );
    }

    public void togglePinStatus(long linkId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Log.d("LinkDao", "开始切换置顶状态, linkId: " + linkId);

        Cursor cursor = db.query(LinkDbHelper.TABLE_LINKS, new String[]{"is_pinned"},
                "_id = ?", new String[]{String.valueOf(linkId)}, null, null, null);

        int currentStatus = 0;
        if (cursor.moveToFirst()) {
            currentStatus = cursor.getInt(0);
            Log.d("LinkDao", "当前置顶状态: " + currentStatus);
        }
        cursor.close();

        ContentValues values = new ContentValues();
        values.put("is_pinned", currentStatus == 0 ? 1 : 0);

        int updatedRows = db.update(LinkDbHelper.TABLE_LINKS, values, "_id = ?",
                new String[]{String.valueOf(linkId)});
        Log.d("LinkDao", "更新结果: " + updatedRows + " 行受影响");
    }

    public void updateSummary(long linkId, String summary) {
        ContentValues values = new ContentValues();
        values.put(LinkDbHelper.COLUMN_SUMMARY, summary);
        database.update(LinkDbHelper.TABLE_LINKS, values,
                LinkDbHelper.COLUMN_ID + " = ?",
                new String[]{String.valueOf(linkId)});
    }

    public void updateClickCount(long id, int count) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("click_count", count);
            db.update("links", values, "_id = ?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void _________(){}

    public List<LinkItem> getAllLinks() {
        List<LinkItem> links = new ArrayList<>();

        Cursor cursor = database.query(
                LinkDbHelper.TABLE_LINKS,
                null,
                null,
                null,
                null,
                null,
                LinkDbHelper.COLUMN_TIMESTAMP + " DESC"
        );

        if (cursor.moveToFirst()) {
            do {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_ID));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_TITLE));
                String url = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_URL));
                String summary = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_SUMMARY));
                String sourceApp = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_SOURCE_APP));
                String originalIntent = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_ORIGINAL_INTENT));
                String targetActivity = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_TARGET_ACTIVITY));
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_TIMESTAMP));
                int clickCount = cursor.getInt(cursor.getColumnIndexOrThrow("click_count"));  // 读取 click_count 字段
                
                LinkItem item = new LinkItem(title, url, sourceApp, originalIntent, targetActivity, timestamp);
                item.setId(id);  // 设置 id
                item.setSummary(summary);
                item.setClickCount(clickCount);  // 设置 clickCount
                
                // 加载该链接的标签
                List<String> tags = getLinkTags(id);
                for (String tag : tags) {
                    item.addTag(tag);
                }
                
                links.add(item);
            } while (cursor.moveToNext());
        }
        cursor.close();

        return links;
    }

    // 获取按日期分组的链接
    public Map<String, List<LinkItem>> getLinksGroupByDate() {
        Map<String, List<LinkItem>> groupedLinks = new TreeMap<>(Collections.reverseOrder());
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        List<LinkItem> allLinks = getAllLinks();
        for (LinkItem link : allLinks) {
            String date = dateFormat.format(new Date(link.getTimestamp()));
            groupedLinks.computeIfAbsent(date, k -> new ArrayList<>()).add(link);
        }

        return groupedLinks;
    }

    public List<LinkItem> getLinksWithoutTags() {
        List<LinkItem> links = new ArrayList<>();

        // 查找没有任何标签的链接，按时间戳降序排序
        String query = "SELECT * FROM " + LinkDbHelper.TABLE_LINKS + " l " +
                "WHERE NOT EXISTS (SELECT 1 FROM " + LinkDbHelper.TABLE_LINK_TAGS +
                " lt WHERE l." + LinkDbHelper.COLUMN_ID + " = lt." + LinkDbHelper.COLUMN_LINK_ID + ") " +
                "ORDER BY " + LinkDbHelper.COLUMN_TIMESTAMP + " DESC";

        Cursor cursor = database.rawQuery(query, null);
        if (cursor.moveToFirst()) {
            do {
                LinkItem item = createLinkItemFromCursor(cursor);
                links.add(item);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return links;
    }

    public List<LinkItem> getLinksByTag(String tag) {
        List<LinkItem> links = new ArrayList<>();
        String query = "SELECT DISTINCT l.* FROM " + LinkDbHelper.TABLE_LINKS + " l " +
                "JOIN " + LinkDbHelper.TABLE_LINK_TAGS + " lt ON l." + LinkDbHelper.COLUMN_ID + " = lt." + LinkDbHelper.COLUMN_LINK_ID + " " +
                "JOIN " + LinkDbHelper.TABLE_TAGS + " t ON lt." + LinkDbHelper.COLUMN_TAG_ID_REF + " = t." + LinkDbHelper.COLUMN_TAG_ID + " " +
                "WHERE t." + LinkDbHelper.COLUMN_TAG_NAME + " = ?";

        Cursor cursor = database.rawQuery(query, new String[]{tag});

        if (cursor.moveToFirst()) {
            do {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_ID));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_TITLE));
                String url = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_URL));
                String sourceApp = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_SOURCE_APP));
                String originalIntent = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_ORIGINAL_INTENT));
                String targetActivity = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_TARGET_ACTIVITY));

                LinkItem item = new LinkItem(title, url, sourceApp, originalIntent, targetActivity);
                item.setId(id);

                // 加载该链接的所有标签
                List<String> tags = getLinkTags(id);
                for (String t : tags) {
                    item.addTag(t);
                }

                links.add(item);
            } while (cursor.moveToNext());
        }
        cursor.close();

        return links;
    }

    public List<LinkItem> getLinksByTags(Set<String> tags) {
        List<LinkItem> links = new ArrayList<>();

        // 构建查询语句，按时间戳降序排序
        String query = "SELECT DISTINCT l.* FROM " + LinkDbHelper.TABLE_LINKS + " l " +
                "JOIN " + LinkDbHelper.TABLE_LINK_TAGS + " lt ON l." + LinkDbHelper.COLUMN_ID + " = lt." + LinkDbHelper.COLUMN_LINK_ID + " " +
                "JOIN " + LinkDbHelper.TABLE_TAGS + " t ON lt." + LinkDbHelper.COLUMN_TAG_ID_REF + " = t." + LinkDbHelper.COLUMN_TAG_ID + " " +
                "WHERE t." + LinkDbHelper.COLUMN_TAG_NAME + " IN (" + makePlaceholders(tags.size()) + ") " +
                "ORDER BY l." + LinkDbHelper.COLUMN_TIMESTAMP + " DESC";

        String[] selectionArgs = tags.toArray(new String[0]);
        Cursor cursor = database.rawQuery(query, selectionArgs);

        if (cursor.moveToFirst()) {
            do {
                LinkItem item = createLinkItemFromCursor(cursor);
                links.add(item);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return links;
    }

    public List<LinkItem> getPinnedLinks() {
        List<LinkItem> pinnedLinks = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Log.d("LinkDao", "获取置顶链接");
        Cursor cursor = db.query(LinkDbHelper.TABLE_LINKS, null, "is_pinned = 1", null,
                null, null, "timestamp DESC");

        Log.d("LinkDao", "找到 " + cursor.getCount() + " 个置顶链接");
        if (cursor.moveToFirst()) {
            do {
                LinkItem item = cursorToLinkItem(cursor);
                List<String> tags = getLinkTags(item.getId());
                for (String tag : tags) {
                    item.addTag(tag);
                }
                pinnedLinks.add(item);
            } while (cursor.moveToNext());
        }
        cursor.close();

        return pinnedLinks;
    }



    public void ___________________________________(){}
    ///////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////





    // 获取链接的所有标签
    public List<String> getLinkTags(long linkId) {
        List<String> tags = new ArrayList<>();
        String query = "SELECT " + LinkDbHelper.COLUMN_TAG_NAME +
                " FROM " + LinkDbHelper.TABLE_TAGS +
                " JOIN " + LinkDbHelper.TABLE_LINK_TAGS +
                " ON " + LinkDbHelper.TABLE_TAGS + "." + LinkDbHelper.COLUMN_TAG_ID +
                " = " + LinkDbHelper.TABLE_LINK_TAGS + "." + LinkDbHelper.COLUMN_TAG_ID_REF +
                " WHERE " + LinkDbHelper.COLUMN_LINK_ID + " = ?";
        
        Cursor cursor = database.rawQuery(query, new String[]{String.valueOf(linkId)});
        if (cursor.moveToFirst()) {
            do {
                tags.add(cursor.getString(0));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return tags;
    }

    // 获取所有标签
    public List<String> getAllTags() {
        List<String> tags = new ArrayList<>();
        Log.d("LinkDao", "Getting all tags");
        Cursor cursor = database.query(
                LinkDbHelper.TABLE_TAGS,
                new String[]{LinkDbHelper.COLUMN_TAG_NAME},
                null, null, null, null, null);
        
        Log.d("LinkDao", "Cursor count: " + cursor.getCount());
        if (cursor.moveToFirst()) {
            do {
                String tag = cursor.getString(0);
                Log.d("LinkDao", "Found tag: " + tag);
                tags.add(tag);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return tags;
    }

    public void updateLinkTags(LinkItem item) {
        // 先删除该链接的所有标签
        database.delete(
                LinkDbHelper.TABLE_LINK_TAGS,
                LinkDbHelper.COLUMN_LINK_ID + " = ?",
                new String[]{String.valueOf(item.getId())}
        );

        // 重新添加所有标签
        for (String tagName : item.getTags()) {
            // 先确保标签存在
            long tagId = getOrCreateTag(tagName);
            // 添加链接-标签关联
            addTagToLink(item.getId(), tagId);
        }
    }

    private long getOrCreateTag(String tagName) {
        // 查找标签是否存在
        Cursor cursor = database.query(
                LinkDbHelper.TABLE_TAGS,
                new String[]{LinkDbHelper.COLUMN_TAG_ID},
                LinkDbHelper.COLUMN_TAG_NAME + " = ?",
                new String[]{tagName},
                null, null, null
        );

        if (cursor.moveToFirst()) {
            long tagId = cursor.getLong(0);
            cursor.close();
            return tagId;
        }

        // 如果标签不存在，创建新标签
        cursor.close();
        return addTag(tagName);
    }

    public void deleteTag(String tag) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            // 从链接-标签关系表中删除该标签的所有关联
            db.delete(
                    LinkDbHelper.TABLE_LINK_TAGS,
                    "tag = ?",
                    new String[]{tag}
            );
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // 添加标签
    public long addTag(String tagName) {
        ContentValues values = new ContentValues();
        values.put(LinkDbHelper.COLUMN_TAG_NAME, tagName);
        return database.insert(LinkDbHelper.TABLE_TAGS, null, values);
    }

    // 私有方法：使用ID添加标签
    private void addTagToLink(long linkId, long tagId) {
        ContentValues values = new ContentValues();
        values.put(LinkDbHelper.COLUMN_LINK_ID, linkId);
        values.put(LinkDbHelper.COLUMN_TAG_ID_REF, tagId);
        database.insert(LinkDbHelper.TABLE_LINK_TAGS, null, values);
    }

    // 公开方法：使用标签名称添加标签
    public void addTagToLink(long linkId, String tagName) {
        // 先确保标签存在，并获取标签ID
        long tagId = getOrCreateTag(tagName);
        // 添加链接-标签关联
        addTagToLink(linkId, tagId);
    }

    public void __________________________________(){}

    // 辅助方法：从游标创建 LinkItem 对象
    private LinkItem createLinkItemFromCursor(Cursor cursor) {
        long id = cursor.getLong(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_ID));
        String title = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_TITLE));
        String url = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_URL));
        String summary = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_SUMMARY));
        String sourceApp = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_SOURCE_APP));
        String originalIntent = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_ORIGINAL_INTENT));
        String targetActivity = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_TARGET_ACTIVITY));
        long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_TIMESTAMP));
        int clickCount = cursor.getInt(cursor.getColumnIndexOrThrow("click_count"));  // 读取 click_count 字段

        // mark
        LinkItem item = new LinkItem(title, url, sourceApp, originalIntent, targetActivity, timestamp);
        item.setId(id);
        item.setSummary(summary);
        item.setClickCount(clickCount);
        
        // 加载该链接的标签
        List<String> tags = getLinkTags(id);
        for (String tag : tags) {
            item.addTag(tag);
        }
        
        return item;
    }

    private LinkItem cursorToLinkItem(Cursor cursor) {
        long id = cursor.getLong(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_ID));
        String title = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_TITLE));
        String url = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_URL));
        String summary = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_SUMMARY));
        String sourceApp = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_SOURCE_APP));
        String originalIntent = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_ORIGINAL_INTENT));
        String targetActivity = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_TARGET_ACTIVITY));
        long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_TIMESTAMP));
        boolean isPinned = cursor.getInt(cursor.getColumnIndexOrThrow("is_pinned")) == 1;
        int clickCount = cursor.getInt(cursor.getColumnIndexOrThrow("click_count"));  // 读取 click_count 字段


        LinkItem item = new LinkItem(title, url, sourceApp, originalIntent, targetActivity, timestamp);
        item.setId(id);
        item.setSummary(summary);
        item.setPinned(isPinned);
        item.setClickCount(clickCount);
        return item;
    }

    // 辅助方法：生成占位符
    private String makePlaceholders(int count) {
        if (count < 1) return "";
        StringBuilder sb = new StringBuilder(count * 2 - 1);
        sb.append("?");
        for (int i = 1; i < count; i++) {
            sb.append(",?");
        }
        return sb.toString();
    }



    public Map<String, Integer> getDailyStatistics() {
        Log.d(TAG, "getDailyStatistics: 开始查询每日统计数据");
        Map<String, Integer> statistics = new HashMap<>();
        Cursor cursor = null;
        try {
            String query = "SELECT date(timestamp/1000, 'unixepoch') as date, COUNT(*) as count " +
                          "FROM links GROUP BY date(timestamp/1000, 'unixepoch')";
            Log.d(TAG, "getDailyStatistics: SQL查询: " + query);
            cursor = database.rawQuery(query, null);
            
            Log.d(TAG, "getDailyStatistics: 查询结果行数: " + cursor.getCount());
            while (cursor.moveToNext()) {
                String date = cursor.getString(0);
                int count = cursor.getInt(1);
                Log.d(TAG, "getDailyStatistics: 统计数据: " + date + " -> " + count);
                statistics.put(date, count);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        Log.d(TAG, "getDailyStatistics: 统计完成，共 " + statistics.size() + " 条数据");
        return statistics;
    }

    public Cursor getClickStatistics(String period) {
        String query = "SELECT strftime('%Y-%m', datetime(timestamp/1000, 'unixepoch')) as period, " +
                "SUM(click_count) as total_clicks " +
                "FROM links " +
                "GROUP BY period " +
                "ORDER BY period DESC";
        
        if ("week".equals(period)) {
            query = "SELECT strftime('%Y-%W', datetime(timestamp/1000, 'unixepoch')) as period, " +
                    "SUM(click_count) as total_clicks " +
                    "FROM links " +
                    "GROUP BY period " +
                    "ORDER BY period DESC";
        }
        
        return database.rawQuery(query, null);
    }
} 