package person.notfresh.readingshare.eventlog;

import android.database.sqlite.SQLiteDatabase;

final class EventLogSchema {

    static final int SCHEMA_VERSION = 2;

    static final String TABLE_EVENTS = "events";

    static final String COL_ID = "id";
    static final String COL_TOPIC = "topic";
    static final String COL_PROCESS_TIME = "process_time";
    static final String COL_EVENT_TIME = "event_time";
    static final String COL_DEVICE_ID = "device_id";
    static final String COL_ENTITY_ID = "entity_id";
    static final String COL_ACTION = "action";
    static final String COL_DATA = "data";

    static final String TABLE_META = "eventlog_meta";

    static final String COL_META_KEY = "key";
    static final String COL_META_VALUE = "value";

    static final String TABLE_SYNC_LOG = "eventlog_sync_log";

    static final String COL_LOG_ID = "id";
    static final String COL_LOG_TIMESTAMP = "timestamp";
    static final String COL_LOG_DIRECTION = "direction";
    static final String COL_LOG_SUCCESS = "success";
    static final String COL_LOG_SENT_COUNT = "sent_count";
    static final String COL_LOG_RECEIVED_COUNT = "received_count";
    static final String COL_LOG_ERROR_MESSAGE = "error_message";

    private EventLogSchema() {}

    static void ensureSchema(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS " + TABLE_EVENTS + " (" +
                        COL_ID + " TEXT PRIMARY KEY, " +
                        COL_TOPIC + " TEXT NOT NULL, " +
                        COL_PROCESS_TIME + " TEXT NOT NULL, " +
                        COL_EVENT_TIME + " TEXT NOT NULL, " +
                        COL_DEVICE_ID + " TEXT NOT NULL, " +
                        COL_ENTITY_ID + " TEXT NOT NULL, " +
                        COL_ACTION + " TEXT NOT NULL, " +
                        COL_DATA + " TEXT" +
                        ")"
        );
        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_events_topic_event " +
                        "ON " + TABLE_EVENTS + "(" + COL_TOPIC + ", " + COL_EVENT_TIME + ")"
        );
        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_events_entity " +
                        "ON " + TABLE_EVENTS + "(" + COL_TOPIC + ", " + COL_ENTITY_ID + ")"
        );
        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_events_topic_process " +
                        "ON " + TABLE_EVENTS + "(" + COL_TOPIC + ", " + COL_PROCESS_TIME + ")"
        );
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS " + TABLE_META + " (" +
                        COL_META_KEY + " TEXT PRIMARY KEY, " +
                        COL_META_VALUE + " TEXT NOT NULL" +
                        ")"
        );
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS " + TABLE_SYNC_LOG + " (" +
                        COL_LOG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COL_LOG_TIMESTAMP + " TEXT NOT NULL, " +
                        COL_LOG_DIRECTION + " TEXT NOT NULL, " +
                        COL_LOG_SUCCESS + " INTEGER NOT NULL, " +
                        COL_LOG_SENT_COUNT + " INTEGER NOT NULL DEFAULT 0, " +
                        COL_LOG_RECEIVED_COUNT + " INTEGER NOT NULL DEFAULT 0, " +
                        COL_LOG_ERROR_MESSAGE + " TEXT" +
                        ")"
        );
        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_sync_log_timestamp " +
                        "ON " + TABLE_SYNC_LOG + "(" + COL_LOG_TIMESTAMP + " DESC)"
        );
    }
}
