package person.notfresh.readingshare.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import person.notfresh.readingshare.model.RssSource;
import android.util.Log;
import person.notfresh.readingshare.model.RssEntry;

public class RssDao {
    private static final String TAG = "RssDao";
    private LinkDbHelper dbHelper;

    public RssDao(Context context) {
        dbHelper = new LinkDbHelper(context);
    }

    public long insertSource(RssSource source) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("url", source.getUrl());
        values.put("name", source.getName());
        values.put("last_update", source.getLastUpdate());
        return db.insert("rss_sources", null, values);
    }

    public long insertEntry(RssEntry entry, int sourceId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("source_id", sourceId);
        values.put("title", entry.getTitle());
        values.put("link", entry.getLink());
        values.put("pub_date", entry.getPublishedDate() != null ? 
                entry.getPublishedDate().getTime() : System.currentTimeMillis());
        
        try {
            return db.insert("rss_entries", null, values);
        } catch (Exception e) {
            Log.e(TAG, "Error inserting entry: " + entry.getTitle(), e);
            return -1;
        }
    }

    public void saveEntries(int sourceId, List<RssEntry> entries) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (RssEntry entry : entries) {
                ContentValues values = new ContentValues();
                values.put("source_id", sourceId);
                values.put("title", entry.getTitle());
                values.put("link", entry.getLink());
                values.put("pub_date", entry.getPublishedDate().getTime());
                db.insert("rss_entries", null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<RssSource> getAllSources() {
        List<RssSource> sources = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query("rss_sources", null, null, null, null, null, "last_update DESC");
        
        if (cursor != null && cursor.moveToFirst()) {
            do {
                RssSource source = new RssSource(
                    cursor.getString(cursor.getColumnIndexOrThrow("url")),
                    cursor.getString(cursor.getColumnIndexOrThrow("name"))
                );
                source.setId(cursor.getInt(cursor.getColumnIndexOrThrow("id")));
                source.setLastUpdate(cursor.getLong(cursor.getColumnIndexOrThrow("last_update")));
                sources.add(source);
            } while (cursor.moveToNext());
            cursor.close();
        }
        return sources;
    }

    public List<RssEntry> getEntriesForSource(int sourceId, int offset, int limit) {
        List<RssEntry> entries = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;
        
        try {
            db = dbHelper.getReadableDatabase(); //@mark
            
            // 构建查询
            String query = "SELECT * FROM rss_entries WHERE source_id = ? ORDER BY pub_date DESC LIMIT ?, ?";
            String[] selectionArgs = new String[]{
                String.valueOf(sourceId),
                String.valueOf(offset),
                String.valueOf(limit)
            };
            
            cursor = db.rawQuery(query, selectionArgs);

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    try {
                        RssEntry entry = new RssEntry(
                            cursor.getString(cursor.getColumnIndexOrThrow("title")),
                            cursor.getString(cursor.getColumnIndexOrThrow("link")),
                            cursor.getLong(cursor.getColumnIndexOrThrow("pub_date"))
                        );
                        entries.add(entry);
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing entry from cursor", e);
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting entries for source " + sourceId, e);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing cursor", e);
                }
            }
        }
        return entries;
    }
} 