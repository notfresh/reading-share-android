package person.notfresh.readingshare.eventlog;

import android.database.sqlite.SQLiteDatabase;

final class EventLogSchema {

    static final int SCHEMA_VERSION = 1;

    static final String TABLE_EVENTS = "events";

    static final String COL_ID = "id";
    static final String COL_TOPIC = "topic";
    static final String COL_PROCESS_TIME = "process_time";
    static final String COL_EVENT_TIME = "event_time";
    static final String COL_DEVICE_ID = "device_id";
    static final String COL_ENTITY_ID = "entity_id";
    static final String COL_ACTION = "action";
    static final String COL_DATA = "data";

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
    }
}
