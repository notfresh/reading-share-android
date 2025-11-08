package person.notfresh.readingshare.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import org.json.JSONArray;
import org.json.JSONException;

import person.notfresh.readingshare.model.LinkItem;

public class LinkDao {
    private LinkDbHelper dbHelper;
    private SQLiteDatabase database;
    private static final String TAG = "LinkDao";

    public LinkDao(Context context) {
        dbHelper = new LinkDbHelper(context);
    }

    public LinkDao(Context context, String databaseName) {
        dbHelper = new LinkDbHelper(context, databaseName);
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

    /**
     * 删除一个链接及其所有相关标签关联
     */
    public boolean deleteLink(long linkId) {
        Log.d("LinkDao", "删除链接ID: " + linkId);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        boolean success = false;
        
        db.beginTransaction();
        try {
            // 删除链接和标签的关联
            int linkTagsDeleted = db.delete(
                LinkDbHelper.TABLE_LINK_TAGS,
                LinkDbHelper.COLUMN_LINK_ID + " = ?",
                new String[]{String.valueOf(linkId)}
            );
            Log.d("LinkDao", "删除了 " + linkTagsDeleted + " 个标签关联");
            
            // 删除链接本身
            int linksDeleted = db.delete(
                LinkDbHelper.TABLE_LINKS,
                LinkDbHelper.COLUMN_ID + " = ?",
                new String[]{String.valueOf(linkId)}
            );
            Log.d("LinkDao", "删除了 " + linksDeleted + " 个链接记录");
            
            db.setTransactionSuccessful();
            success = (linksDeleted > 0);
        } finally {
            db.endTransaction();
        }
        
        return success;
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
                String remark = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_REMARK));
                String sourceApp = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_SOURCE_APP));
                String originalIntent = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_ORIGINAL_INTENT));
                String targetActivity = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_TARGET_ACTIVITY));
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_TIMESTAMP));
                int clickCount = cursor.getInt(cursor.getColumnIndexOrThrow("click_count"));  // 读取 click_count 字段
                
                LinkItem item = new LinkItem(title, url, sourceApp, originalIntent, targetActivity, timestamp);
                item.setId(id);  // 设置 id
                item.setSummary(summary);
                item.setRemark(remark);
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
        if(tags.isEmpty()){
            return links;
        }
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

    // 添加标签
    public long addTag(String tagName) {
        ContentValues values = new ContentValues();
        values.put(LinkDbHelper.COLUMN_TAG_NAME, tagName);
        return database.insert(LinkDbHelper.TABLE_TAGS, null, values);
    }


    /**
     * 删除标签及其所有关联
     * @param tag 要删除的标签名
     */
    public void deleteTag(String tag) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        
        try {
            // 1. 先查询标签ID
            long tagId = -1;
            Cursor cursor = db.query(
                    LinkDbHelper.TABLE_TAGS,
                    new String[]{LinkDbHelper.COLUMN_TAG_ID},
                    LinkDbHelper.COLUMN_TAG_NAME + " = ?",
                    new String[]{tag},
                    null, null, null
            );
            
            if (cursor.moveToFirst()) {
                tagId = cursor.getLong(0);
            }
            cursor.close();
            
            if (tagId != -1) {
                // 2. 删除链接-标签关联表中的记录
                Log.d("LinkDao", "删除标签关联: tagId=" + tagId);
                int linkTagsDeleted = db.delete(
                        LinkDbHelper.TABLE_LINK_TAGS,
                        LinkDbHelper.COLUMN_TAG_ID_REF + " = ?",
                        new String[]{String.valueOf(tagId)}
                );
                Log.d("LinkDao", "已删除" + linkTagsDeleted + "条关联记录");
                
                // 3. 最后删除标签本身
                int tagsDeleted = db.delete(
                        LinkDbHelper.TABLE_TAGS,
                        LinkDbHelper.COLUMN_TAG_ID + " = ?",
                        new String[]{String.valueOf(tagId)}
                );
                Log.d("LinkDao", "已删除" + tagsDeleted + "个标签");
            } else {
                Log.w("LinkDao", "未找到标签: " + tag);
            }
            
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e("LinkDao", "删除标签时出错: " + e.getMessage(), e);
        } finally {
            db.endTransaction();
        }
    }

    /**
     * 删除标签及其所有关联,并且删除只有这个标签的链接
     * @param tag 要删除的标签名
     */
    public void deleteTagWithLinks(String tag) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        
        try {
            // 1. 先查询标签ID
            long tagId = -1;
            Cursor cursor = db.query(
                    LinkDbHelper.TABLE_TAGS,
                    new String[]{LinkDbHelper.COLUMN_TAG_ID},
                    LinkDbHelper.COLUMN_TAG_NAME + " = ?",
                    new String[]{tag},
                    null, null, null
            );
            
            if (cursor.moveToFirst()) {
                tagId = cursor.getLong(0);
            }
            cursor.close();
            
            if (tagId != -1) {
                // 2. 查找只有这一个标签的链接
                // 首先找到所有有这个标签的链接ID
                String findLinksQuery = "SELECT " + LinkDbHelper.COLUMN_LINK_ID +
                                       " FROM " + LinkDbHelper.TABLE_LINK_TAGS + 
                                       " WHERE " + LinkDbHelper.COLUMN_TAG_ID_REF + " = ?";
                
                Cursor linksCursor = db.rawQuery(findLinksQuery, new String[]{String.valueOf(tagId)});
                
                // 保存要删除的链接ID列表
                List<Long> linksToDelete = new ArrayList<>();
                
                while (linksCursor.moveToNext()) {
                    long linkId = linksCursor.getLong(0);
                    
                    // 检查这个链接是否只有这一个标签
                    String countTagsQuery = "SELECT COUNT(*) FROM " + LinkDbHelper.TABLE_LINK_TAGS + 
                                           " WHERE " + LinkDbHelper.COLUMN_LINK_ID + " = ?";
                    
                    Cursor tagCountCursor = db.rawQuery(countTagsQuery, new String[]{String.valueOf(linkId)});
                    
                    if (tagCountCursor.moveToFirst() && tagCountCursor.getInt(0) <= 1) {
                        // 链接只有这一个标签，加入待删除列表
                        linksToDelete.add(linkId);
                    }
                    
                    tagCountCursor.close();
                }
                linksCursor.close();
                
                // 3. 删除只有一个标签的链接
                for (long linkId : linksToDelete) {
                    Log.d("LinkDao", "删除仅有此标签的链接: linkId=" + linkId);
                    db.delete(
                            LinkDbHelper.TABLE_LINKS,
                            LinkDbHelper.COLUMN_ID + " = ?",
                            new String[]{String.valueOf(linkId)}
                    );
                }
                
                // 4. 删除链接-标签关联
                int linkTagsDeleted = db.delete(
                        LinkDbHelper.TABLE_LINK_TAGS,
                        LinkDbHelper.COLUMN_TAG_ID_REF + " = ?",
                        new String[]{String.valueOf(tagId)}
                );
                Log.d("LinkDao", "已删除" + linkTagsDeleted + "条标签关联");
                
                // 5. 删除标签本身
                int tagsDeleted = db.delete(
                        LinkDbHelper.TABLE_TAGS,
                        LinkDbHelper.COLUMN_TAG_ID + " = ?",
                        new String[]{String.valueOf(tagId)}
                );
                Log.d("LinkDao", "已删除标签: " + tag);
            } else {
                Log.w("LinkDao", "未找到标签: " + tag);
            }
            
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e("LinkDao", "删除标签及链接时出错: " + e.getMessage(), e);
        } finally {
            db.endTransaction();
        }
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

    /**
     * 获取所有标签及其使用次数（按配置的排序）
     * @return Map<String, Integer> 标签名称和使用次数的映射
     */
    public Map<String, Integer> getTagsWithCount() {
        Map<String, Integer> tagCountMap = new LinkedHashMap<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        
        // 1. 获取配置的排序
        List<Long> orderedTagIds = getTagOrder();
        Set<Long> orderedSet = new HashSet<>(orderedTagIds);
        
        // 2. 获取所有标签及计数（不排序）
        Map<Long, TagCountInfo> allTags = new HashMap<>();
        Cursor cursor = db.rawQuery(
            "SELECT t." + LinkDbHelper.COLUMN_TAG_ID + 
            ", t." + LinkDbHelper.COLUMN_TAG_NAME + 
            ", COUNT(lt." + LinkDbHelper.COLUMN_LINK_ID + ") as count " +
            "FROM " + LinkDbHelper.TABLE_TAGS + " t " +
            "LEFT JOIN " + LinkDbHelper.TABLE_LINK_TAGS + " lt " +
            "ON t." + LinkDbHelper.COLUMN_TAG_ID + " = lt." + LinkDbHelper.COLUMN_TAG_ID_REF + " " +
            "GROUP BY t." + LinkDbHelper.COLUMN_TAG_ID + ", t." + LinkDbHelper.COLUMN_TAG_NAME, null);
        
        if (cursor.moveToFirst()) {
            do {
                long tagId = cursor.getLong(0);
                String tagName = cursor.getString(1);
                int count = cursor.getInt(2);
                if (count > 0) {  // 只保存有链接的标签
                    allTags.put(tagId, new TagCountInfo(tagName, count));
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
        
        // 3. 按配置的顺序添加
        for (Long tagId : orderedTagIds) {
            TagCountInfo info = allTags.get(tagId);
            if (info != null) {
                tagCountMap.put(info.name, info.count);
            }
        }
        
        // 4. 添加未在配置中的新标签（按tag_id排序）
        List<Long> unorderedTagIds = new ArrayList<>();
        for (Long tagId : allTags.keySet()) {
            if (!orderedSet.contains(tagId)) {
                unorderedTagIds.add(tagId);
            }
        }
        Collections.sort(unorderedTagIds);
        for (Long tagId : unorderedTagIds) {
            TagCountInfo info = allTags.get(tagId);
            if (info != null) {
                tagCountMap.put(info.name, info.count);
            }
        }
        
        return tagCountMap;
    }
    
    // 辅助类
    private static class TagCountInfo {
        String name;
        int count;
        TagCountInfo(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }

    public void updateLinkRemark(long linkId, String remark) {
        ContentValues values = new ContentValues();
        values.put(LinkDbHelper.COLUMN_REMARK, remark);
        
        database.update(
                LinkDbHelper.TABLE_LINKS, 
                values, 
                LinkDbHelper.COLUMN_ID + " = ?", 
                new String[]{String.valueOf(linkId)}
        );
        
        Log.d("LinkDao", "更新链接备注: linkId=" + linkId + ", remark is " + remark);
    }

    /**
     * 保存标签排序
     * @param tagIds 按排序顺序排列的标签ID列表
     */
    public void saveTagOrder(List<Long> tagIds) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (Long tagId : tagIds) {
                jsonArray.put(tagId);
            }
            
            ContentValues values = new ContentValues();
            values.put(LinkDbHelper.COLUMN_CONFIG_KEY, "tag_order");
            values.put(LinkDbHelper.COLUMN_CONFIG_VALUE, jsonArray.toString());
            
            // 先删除，再插入（实现 INSERT OR REPLACE）
            database.delete(LinkDbHelper.TABLE_CONFIG,
                LinkDbHelper.COLUMN_CONFIG_KEY + " = ?",
                new String[]{"tag_order"});
            database.insert(LinkDbHelper.TABLE_CONFIG, null, values);
            
            Log.d("LinkDao", "保存标签排序: " + tagIds.size() + " 个标签");
        } catch (Exception e) {
            Log.e("LinkDao", "保存标签排序失败", e);
        }
    }

    /**
     * 获取标签排序
     * @return 按排序顺序排列的标签ID列表，如果不存在则返回空列表
     */
    public List<Long> getTagOrder() {
        List<Long> tagIds = new ArrayList<>();
        Cursor cursor = database.query(
            LinkDbHelper.TABLE_CONFIG,
            new String[]{LinkDbHelper.COLUMN_CONFIG_VALUE},
            LinkDbHelper.COLUMN_CONFIG_KEY + " = ?",
            new String[]{"tag_order"},
            null, null, null);
        
        if (cursor.moveToFirst()) {
            try {
                String value = cursor.getString(0);
                JSONArray jsonArray = new JSONArray(value);
                for (int i = 0; i < jsonArray.length(); i++) {
                    tagIds.add(jsonArray.getLong(i));
                }
                Log.d("LinkDao", "获取标签排序: " + tagIds.size() + " 个标签");
            } catch (JSONException e) {
                Log.e("LinkDao", "解析标签排序失败", e);
            }
        }
        cursor.close();
        return tagIds;
    }

    /**
     * 根据标签ID获取标签名称
     */
    public String getTagNameById(long tagId) {
        Cursor cursor = database.query(
            LinkDbHelper.TABLE_TAGS,
            new String[]{LinkDbHelper.COLUMN_TAG_NAME},
            LinkDbHelper.COLUMN_TAG_ID + " = ?",
            new String[]{String.valueOf(tagId)},
            null, null, null);
        
        String tagName = null;
        if (cursor.moveToFirst()) {
            tagName = cursor.getString(0);
        }
        cursor.close();
        return tagName;
    }

    /**
     * 根据标签名称获取标签ID
     */
    public long getTagIdByName(String tagName) {
        Cursor cursor = database.query(
            LinkDbHelper.TABLE_TAGS,
            new String[]{LinkDbHelper.COLUMN_TAG_ID},
            LinkDbHelper.COLUMN_TAG_NAME + " = ?",
            new String[]{tagName},
            null, null, null);
        
        long tagId = -1;
        if (cursor.moveToFirst()) {
            tagId = cursor.getLong(0);
        }
        cursor.close();
        return tagId;
    }
} 