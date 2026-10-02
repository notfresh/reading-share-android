package person.notfresh.readingshare.eventlog;

import java.util.List;

public interface EventLogStore {

    void append(EventRecord record);

    List<EventRecord> since(String topic, String sinceEventTime);

    List<EventRecord> since(String topic, String sinceEventTime, int limit);

    List<EventRecord> until(String topic, String untilEventTime, int limit);

    /**
     * Pull events whose process_time is strictly greater than the given cursor.
     * Used by PUSH (§10.5) to enumerate local events that have not yet been
     * sent to a remote server. Ordered by process_time ASC.
     */
    List<EventRecord> sinceByProcessTime(String topic, String sinceProcessTime);

    /**
     * Pull events whose process_time is strictly less than the given cursor,
     * newest first. Used by PULL (§10.5) reverse-walk helpers (debug/UI).
     */
    List<EventRecord> untilByProcessTime(String topic, String untilProcessTime, int limit);

    int count(String topic);

    void deleteAll();

    EventRecord latest(String topic);

    String deviceId();
}
