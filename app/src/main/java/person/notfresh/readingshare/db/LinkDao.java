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

import person.notfresh.readingshare.model.LinkItem;

public class LinkDao {
    private LinkDbHelper dbHelper;
    private SQLiteDatabase database;

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
        values.put(LinkDbHelper.COLUMN_REMARK, item.getRemark()); // 添加备注列

        long linkId = database.insert(LinkDbHelper.TABLE_LINKS, null, values);
        item.setId(linkId);
        updateLinkTags(item);
        return linkId;
    }

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
                String sourceApp = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_SOURCE_APP));
                String originalIntent = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_ORIGINAL_INTENT));
                String targetActivity = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_TARGET_ACTIVITY));
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_TIMESTAMP));
                
                LinkItem item = new LinkItem(title, url, sourceApp, originalIntent, targetActivity, timestamp);
                item.setId(id);  // 设置 id
                
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

    public void deleteLink(String url) {
        database.delete(
                LinkDbHelper.TABLE_LINKS,
                LinkDbHelper.COLUMN_URL + " = ?",
                new String[]{url}
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

    // 辅助方法：从游标创建 LinkItem 对象
    private LinkItem createLinkItemFromCursor(Cursor cursor) {
        long id = cursor.getLong(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_ID));
        String title = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_TITLE));
        String url = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_URL));
        String sourceApp = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_SOURCE_APP));
        String originalIntent = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_ORIGINAL_INTENT));
        String targetActivity = cursor.getString(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_TARGET_ACTIVITY));
        long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(LinkDbHelper.COLUMN_TIMESTAMP));

        LinkItem item = new LinkItem(title, url, sourceApp, originalIntent, targetActivity, timestamp);
        item.setId(id);
        
        // 加载该链接的标签
        List<String> tags = getLinkTags(id);
        for (String tag : tags) {
            item.addTag(tag);
        }
        
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
} 