package person.notfresh.readingshare.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;
import com.rometools.rome.feed.synd.SyndEntry;
import person.notfresh.readingshare.model.RssSource;

public class RssDao {
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

    public void saveEntries(int sourceId, List<SyndEntry> entries) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (SyndEntry entry : entries) {
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

    public List<SyndEntry> getEntriesForSource(int sourceId) {
        List<SyndEntry> entries = new ArrayList<>();
        // Implementation for retrieving entries
        return entries;
    }
} 