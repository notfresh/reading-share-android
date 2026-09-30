package person.notfresh.readingshare.eventlog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public final class EventLogClient {

    private static volatile EventLogClient INSTANCE;

    private final EventLogStore store;
    private final TimeSource clock;
    private volatile boolean bootstrapped = false;

    private EventLogClient(EventLogStore store, TimeSource clock) {
        this.store = store;
        this.clock = clock;
    }

    public static EventLogClient get() {
        EventLogClient c = INSTANCE;
        if (c == null) {
            throw new EventLogException(
                    "EventLogClient not initialized; call init() first");
        }
        return c;
    }

    public static synchronized void init(EventLogStore store) {
        init(store, System::currentTimeMillis);
    }

    public static synchronized void init(EventLogStore store, TimeSource clock) {
        INSTANCE = new EventLogClient(store, clock);
    }

    public static synchronized void reset() {
        INSTANCE = null;
    }

    public EventLogStore store() {
        return store;
    }

    public boolean isBootstrapped() {
        return bootstrapped;
    }

    public void setBootstrapFlag() {
        this.bootstrapped = true;
    }

    public void resetBootstrapFlag() {
        this.bootstrapped = false;
    }

    public EventRecord create(String topic, String entityId, long eventTimeMillis, String dataJson) {
        return record(topic, entityId, eventTimeMillis, EventAction.CREATE, dataJson);
    }

    public EventRecord update(String topic, String entityId, long eventTimeMillis, String dataJson) {
        return record(topic, entityId, eventTimeMillis, EventAction.UPDATE, dataJson);
    }

    public EventRecord delete(String topic, String entityId, long eventTimeMillis) {
        return record(topic, entityId, eventTimeMillis, EventAction.DELETE, null);
    }

    public List<EventRecord> since(String topic, String sinceEventTime) {
        return store.since(topic, sinceEventTime);
    }

    public List<EventRecord> since(String topic, String sinceEventTime, int limit) {
        return store.since(topic, sinceEventTime, limit);
    }

    public List<EventRecord> until(String topic, String untilEventTime, int limit) {
        return store.until(topic, untilEventTime, limit);
    }

    public EventRecord latest(String topic) {
        return store.latest(topic);
    }

    public int count(String topic) {
        return store.count(topic);
    }

    public void deleteAll() {
        store.deleteAll();
    }

    private EventRecord record(String topic, String entityId, long eventTimeMillis,
                               EventAction action, String dataJson) {
        if (topic == null || topic.isEmpty()) {
            throw new EventLogException("topic is empty");
        }
        if (entityId == null || entityId.isEmpty()) {
            throw new EventLogException("entityId is empty");
        }
        // event_time: 实体的真实创建时间,本地时间(ISO-8601 带偏移)
        String eventTime = formatLocalIso8601(eventTimeMillis);
        // process_time: 日志生成时刻,UTC(ISO-8601 + Z)
        long now = clock.nowMillis();
        String processTime = formatIso8601(now);
        String deviceId = store.deviceId();
        String id = computeId(topic, deviceId, eventTime, entityId, action.name());
        EventRecord r = new EventRecord(id, topic, processTime, eventTime,
                deviceId, entityId, action, dataJson);
        store.append(r);
        return r;
    }

    public static String computeId(String topic, String deviceId, String eventTime,
                            String entityId, String action) {
        String raw = topic + "|" + deviceId + "|" + eventTime + "|"
                + entityId + "|" + action;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                sb.append(String.format(Locale.US, "%02x", digest[i] & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new EventLogException("SHA-256 unavailable", e);
        }
    }

    public static String formatIso8601(long millis) {
        SimpleDateFormat f = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(millis));
    }

    /**
     * Format millis as ISO-8601 with local timezone offset, e.g.
     * "2026-09-03T14:23:25.123+08:00". Used for event_time which represents
     * the entity's true creation moment in user's local time.
     */
    public static String formatLocalIso8601(long millis) {
        SimpleDateFormat f = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US);
        f.setTimeZone(TimeZone.getDefault());
        return f.format(new Date(millis));
    }

    public interface TimeSource {
        long nowMillis();
    }
}
