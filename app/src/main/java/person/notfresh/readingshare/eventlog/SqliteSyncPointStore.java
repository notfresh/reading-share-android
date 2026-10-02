package person.notfresh.readingshare.eventlog;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import static person.notfresh.readingshare.eventlog.EventLogSchema.COL_META_KEY;
import static person.notfresh.readingshare.eventlog.EventLogSchema.COL_META_VALUE;
import static person.notfresh.readingshare.eventlog.EventLogSchema.TABLE_META;

/**
 * SQLite-backed {@link SyncPointStore}. Uses the {@code eventlog_meta} table
 * defined in {@link EventLogSchema}. Schema is created lazily on first
 * construction via {@link EventLogSchema#ensureSchema(SQLiteDatabase)}.
 */
public final class SqliteSyncPointStore implements SyncPointStore {

    private final SQLiteDatabase db;

    public SqliteSyncPointStore(SQLiteDatabase db) {
        if (db == null) {
            throw new EventLogException("db is null");
        }
        this.db = db;
        EventLogSchema.ensureSchema(db);
    }

    @Override
    public String get(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        Cursor c = db.rawQuery(
                "SELECT " + COL_META_VALUE + " FROM " + TABLE_META +
                        " WHERE " + COL_META_KEY + " = ?",
                new String[]{key});
        try {
            if (c.moveToFirst()) {
                return c.getString(0);
            }
            return null;
        } finally {
            c.close();
        }
    }

    @Override
    public void set(String key, String value) {
        if (key == null || key.isEmpty()) {
            throw new EventLogException("key is empty");
        }
        if (value == null) {
            throw new EventLogException("value is null");
        }
        ContentValues cv = new ContentValues();
        cv.put(COL_META_KEY, key);
        cv.put(COL_META_VALUE, value);
        db.insertWithOnConflict(TABLE_META, null, cv,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isEmpty()) {
            return;
        }
        db.delete(TABLE_META,
                COL_META_KEY + " = ?",
                new String[]{key});
    }
}
