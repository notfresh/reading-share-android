package person.notfresh.readingshare.eventlog;

import java.util.List;

public interface EventLogStore {

    void append(EventRecord record);

    List<EventRecord> since(String topic, String sinceEventTime);

    List<EventRecord> since(String topic, String sinceEventTime, int limit);

    List<EventRecord> until(String topic, String untilEventTime, int limit);

    int count(String topic);

    void deleteAll();

    EventRecord latest(String topic);

    String deviceId();
}
