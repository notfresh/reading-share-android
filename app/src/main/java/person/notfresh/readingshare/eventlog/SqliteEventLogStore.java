package person.notfresh.readingshare.eventlog;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import static person.notfresh.readingshare.eventlog.EventLogSchema.COL_ACTION;
import static person.notfresh.readingshare.eventlog.EventLogSchema.COL_DATA;
import static person.notfresh.readingshare.eventlog.EventLogSchema.COL_DEVICE_ID;
import static person.notfresh.readingshare.eventlog.EventLogSchema.COL_ENTITY_ID;
import static person.notfresh.readingshare.eventlog.EventLogSchema.COL_EVENT_TIME;
import static person.notfresh.readingshare.eventlog.EventLogSchema.COL_ID;
import static person.notfresh.readingshare.eventlog.EventLogSchema.COL_PROCESS_TIME;
import static person.notfresh.readingshare.eventlog.EventLogSchema.COL_TOPIC;
import static person.notfresh.readingshare.eventlog.EventLogSchema.TABLE_EVENTS;

public final class SqliteEventLogStore implements EventLogStore {

    private final SQLiteDatabase db;
    private final String deviceId;

    public SqliteEventLogStore(SQLiteDatabase db, String deviceId) {
        if (db == null) throw new EventLogException("db is null");
        if (deviceId == null || deviceId.isEmpty()) {
            throw new EventLogException("deviceId is empty");
        }
        this.db = db;
        this.deviceId = deviceId;
        EventLogSchema.ensureSchema(db);
    }

    @Override
    public void append(EventRecord record) {
        ContentValues cv = new ContentValues();
        cv.put(COL_ID, record.getId());
        cv.put(COL_TOPIC, record.getTopic());
        cv.put(COL_PROCESS_TIME, record.getProcessTime());
        cv.put(COL_EVENT_TIME, record.getEventTime());
        cv.put(COL_DEVICE_ID, record.getDeviceId());
        cv.put(COL_ENTITY_ID, record.getEntityId());
        cv.put(COL_ACTION, record.getAction().name());
        cv.put(COL_DATA, record.getData());
        db.insertWithOnConflict(TABLE_EVENTS, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    @Override
    public List<EventRecord> since(String topic, String sinceEventTime) {
        return since(topic, sinceEventTime, 10000);
    }

    @Override
    public List<EventRecord> since(String topic, String sinceEventTime, int limit) {
        if (limit <= 0) {
            throw new EventLogException("limit must be positive, got " + limit);
        }
        int capped = Math.min(limit, 10000);
        String cutoff = sinceEventTime == null ? "" : sinceEventTime;
        String sql = "SELECT " + COL_ID + ", " + COL_TOPIC + ", " + COL_PROCESS_TIME + ", " +
                COL_EVENT_TIME + ", " + COL_DEVICE_ID + ", " + COL_ENTITY_ID + ", " +
                COL_ACTION + ", " + COL_DATA + " FROM " + TABLE_EVENTS +
                " WHERE " + COL_TOPIC + " = ? AND " + COL_EVENT_TIME + " > ?" +
                " ORDER BY " + COL_EVENT_TIME + " ASC LIMIT ?";
        List<EventRecord> out = new ArrayList<>();
        Cursor c = db.rawQuery(sql, new String[]{topic, cutoff, String.valueOf(capped)});
        try {
            while (c.moveToNext()) {
                out.add(readRow(c));
            }
        } finally {
            c.close();
        }
        return out;
    }

    @Override
    public List<EventRecord> until(String topic, String untilEventTime, int limit) {
        if (limit <= 0) {
            throw new EventLogException("limit must be positive, got " + limit);
        }
        int capped = Math.min(limit, 10000);
        // Pass null untilEventTime for "from the newest" — handled by Activity (no cutoff)
        String sql;
        String[] args;
        if (untilEventTime == null) {
            sql = "SELECT " + COL_ID + ", " + COL_TOPIC + ", " + COL_PROCESS_TIME + ", " +
                    COL_EVENT_TIME + ", " + COL_DEVICE_ID + ", " + COL_ENTITY_ID + ", " +
                    COL_ACTION + ", " + COL_DATA + " FROM " + TABLE_EVENTS +
                    " WHERE " + COL_TOPIC + " = ?" +
                    " ORDER BY " + COL_EVENT_TIME + " DESC LIMIT ?";
            args = new String[]{topic, String.valueOf(capped)};
        } else {
            sql = "SELECT " + COL_ID + ", " + COL_TOPIC + ", " + COL_PROCESS_TIME + ", " +
                    COL_EVENT_TIME + ", " + COL_DEVICE_ID + ", " + COL_ENTITY_ID + ", " +
                    COL_ACTION + ", " + COL_DATA + " FROM " + TABLE_EVENTS +
                    " WHERE " + COL_TOPIC + " = ? AND " + COL_EVENT_TIME + " < ?" +
                    " ORDER BY " + COL_EVENT_TIME + " DESC LIMIT ?";
            args = new String[]{topic, untilEventTime, String.valueOf(capped)};
        }
        List<EventRecord> out = new ArrayList<>();
        Cursor c = db.rawQuery(sql, args);
        try {
            while (c.moveToNext()) {
                out.add(readRow(c));
            }
        } finally {
            c.close();
        }
        return out;
    }

    @Override
    public EventRecord latest(String topic) {
        String sql = "SELECT " + COL_ID + ", " + COL_TOPIC + ", " + COL_PROCESS_TIME + ", " +
                COL_EVENT_TIME + ", " + COL_DEVICE_ID + ", " + COL_ENTITY_ID + ", " +
                COL_ACTION + ", " + COL_DATA + " FROM " + TABLE_EVENTS +
                " WHERE " + COL_TOPIC + " = ? ORDER BY " + COL_PROCESS_TIME + " DESC LIMIT 1";
        Cursor c = db.rawQuery(sql, new String[]{topic});
        try {
            if (c.moveToFirst()) {
                return readRow(c);
            }
            return null;
        } finally {
            c.close();
        }
    }

    @Override
    public String deviceId() {
        return deviceId;
    }

    @Override
    public int count(String topic) {
        Cursor c = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_EVENTS + " WHERE " + COL_TOPIC + " = ?",
                new String[]{topic});
        try {
            if (c.moveToFirst()) {
                return c.getInt(0);
            }
            return 0;
        } finally {
            c.close();
        }
    }

    @Override
    public void deleteAll() {
        db.delete(TABLE_EVENTS, null, null);
    }

    private static EventRecord readRow(Cursor c) {
        return new EventRecord(
                c.getString(c.getColumnIndexOrThrow(COL_ID)),
                c.getString(c.getColumnIndexOrThrow(COL_TOPIC)),
                c.getString(c.getColumnIndexOrThrow(COL_PROCESS_TIME)),
                c.getString(c.getColumnIndexOrThrow(COL_EVENT_TIME)),
                c.getString(c.getColumnIndexOrThrow(COL_DEVICE_ID)),
                c.getString(c.getColumnIndexOrThrow(COL_ENTITY_ID)),
                EventAction.valueOf(c.getString(c.getColumnIndexOrThrow(COL_ACTION))),
                c.getString(c.getColumnIndexOrThrow(COL_DATA))
        );
    }
}
