package person.notfresh.myapplication.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;

import person.notfresh.myapplication.model.LinkItem;

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

    public long insertLink(LinkItem link) {
        ContentValues values = new ContentValues();
        values.put(LinkDbHelper.COLUMN_TITLE, link.getTitle());
        values.put(LinkDbHelper.COLUMN_URL, link.getUrl());
        values.put(LinkDbHelper.COLUMN_SOURCE_APP, link.getSourceApp());
        values.put(LinkDbHelper.COLUMN_ORIGINAL_INTENT, link.getOriginalIntent());
        values.put(LinkDbHelper.COLUMN_TARGET_ACTIVITY, link.getTargetActivity());
        values.put(LinkDbHelper.COLUMN_TIMESTAMP, link.getTimestamp());

        return database.insert(LinkDbHelper.TABLE_LINKS, null, values);
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
                
                LinkItem item = new LinkItem(title, url, sourceApp, originalIntent, targetActivity);
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
        Cursor cursor = database.query(
                LinkDbHelper.TABLE_TAGS,
                new String[]{LinkDbHelper.COLUMN_TAG_NAME},
                null, null, null, null, null);
        
        if (cursor.moveToFirst()) {
            do {
                tags.add(cursor.getString(0));
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
} 