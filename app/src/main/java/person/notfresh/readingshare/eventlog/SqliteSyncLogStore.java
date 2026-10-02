package person.notfresh.readingshare.eventlog;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import static person.notfresh.readingshare.eventlog.EventLogSchema.COL_LOG_DIRECTION;
import static person.notfresh.readingshare.eventlog.EventLogSchema.COL_LOG_ERROR_MESSAGE;
import static person.notfresh.readingshare.eventlog.EventLogSchema.COL_LOG_ID;
import static person.notfresh.readingshare.eventlog.EventLogSchema.COL_LOG_RECEIVED_COUNT;
import static person.notfresh.readingshare.eventlog.EventLogSchema.COL_LOG_SENT_COUNT;
import static person.notfresh.readingshare.eventlog.EventLogSchema.COL_LOG_SUCCESS;
import static person.notfresh.readingshare.eventlog.EventLogSchema.COL_LOG_TIMESTAMP;
import static person.notfresh.readingshare.eventlog.EventLogSchema.TABLE_SYNC_LOG;

/**
 * SQLite-backed {@link SyncLogStore}. Schema created lazily via
 * {@link EventLogSchema#ensureSchema(SQLiteDatabase)}.
 */
public final class SqliteSyncLogStore implements SyncLogStore {

    private final SQLiteDatabase db;

    public SqliteSyncLogStore(SQLiteDatabase db) {
        if (db == null) {
            throw new EventLogException("db is null");
        }
        this.db = db;
        EventLogSchema.ensureSchema(db);
    }

    @Override
    public void add(SyncLogEntry.Direction direction, String timestamp,
                    boolean success, int sentCount, int receivedCount,
                    String errorMessage) {
        if (direction == null) {
            throw new EventLogException("direction is null");
        }
        if (timestamp == null || timestamp.isEmpty()) {
            throw new EventLogException("timestamp is empty");
        }
        ContentValues cv = new ContentValues();
        cv.put(COL_LOG_TIMESTAMP, timestamp);
        cv.put(COL_LOG_DIRECTION, direction.value());
        cv.put(COL_LOG_SUCCESS, success ? 1 : 0);
        cv.put(COL_LOG_SENT_COUNT, sentCount);
        cv.put(COL_LOG_RECEIVED_COUNT, receivedCount);
        cv.put(COL_LOG_ERROR_MESSAGE, errorMessage);
        db.insert(TABLE_SYNC_LOG, null, cv);
    }

    @Override
    public List<SyncLogEntry> recent(int limit) {
        if (limit <= 0) {
            throw new EventLogException("limit must be positive, got " + limit);
        }
        String sql = "SELECT " + COL_LOG_ID + ", " + COL_LOG_TIMESTAMP + ", " +
                COL_LOG_DIRECTION + ", " + COL_LOG_SUCCESS + ", " +
                COL_LOG_SENT_COUNT + ", " + COL_LOG_RECEIVED_COUNT + ", " +
                COL_LOG_ERROR_MESSAGE + " FROM " + TABLE_SYNC_LOG +
                " ORDER BY " + COL_LOG_ID + " DESC LIMIT ?";
        List<SyncLogEntry> out = new ArrayList<>();
        Cursor c = db.rawQuery(sql, new String[]{String.valueOf(limit)});
        try {
            while (c.moveToNext()) {
                String dirStr = c.getString(c.getColumnIndexOrThrow(COL_LOG_DIRECTION));
                SyncLogEntry.Direction dir = "pull".equals(dirStr)
                        ? SyncLogEntry.Direction.PULL
                        : SyncLogEntry.Direction.PUSH;
                out.add(new SyncLogEntry(
                        c.getLong(c.getColumnIndexOrThrow(COL_LOG_ID)),
                        c.getString(c.getColumnIndexOrThrow(COL_LOG_TIMESTAMP)),
                        dir,
                        c.getInt(c.getColumnIndexOrThrow(COL_LOG_SUCCESS)) != 0,
                        c.getInt(c.getColumnIndexOrThrow(COL_LOG_SENT_COUNT)),
                        c.getInt(c.getColumnIndexOrThrow(COL_LOG_RECEIVED_COUNT)),
                        c.isNull(c.getColumnIndexOrThrow(COL_LOG_ERROR_MESSAGE))
                                ? null
                                : c.getString(c.getColumnIndexOrThrow(COL_LOG_ERROR_MESSAGE))
                ));
            }
        } finally {
            c.close();
        }
        return out;
    }
}
