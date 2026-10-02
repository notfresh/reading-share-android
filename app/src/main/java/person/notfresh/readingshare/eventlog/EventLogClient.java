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
    private final SyncPointStore syncStore;
    private final EventLogPusher pusher;
    private final EventLogPuller puller;
    private final SyncConfig syncConfig;
    private final SyncLogStore syncLogStore;
    private volatile boolean bootstrapped = false;

    private EventLogClient(EventLogStore store, TimeSource clock,
                           SyncPointStore syncStore,
                           EventLogPusher pusher,
                           EventLogPuller puller,
                           SyncConfig syncConfig,
                           SyncLogStore syncLogStore) {
        this.store = store;
        this.clock = clock;
        this.syncStore = syncStore;
        this.pusher = pusher;
        this.puller = puller;
        this.syncConfig = syncConfig;
        this.syncLogStore = syncLogStore;
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
        init(store, System::currentTimeMillis, null, null, null, null, null);
    }

    public static synchronized void init(EventLogStore store, TimeSource clock) {
        init(store, clock, null, null, null, null, null);
    }

    /**
     * Full init — wires up remote sync. Pass {@code null} for any of
     * {@code syncStore} / {@code pusher} / {@code puller} / {@code syncConfig} /
     * {@code syncLogStore} to keep that subsystem disabled. {@code pushPending}
     * / {@code pull} will then throw {@link EventLogException} when called.
     */
    public static synchronized void init(EventLogStore store,
                                         SyncPointStore syncStore,
                                         EventLogPusher pusher,
                                         EventLogPuller puller,
                                         SyncConfig syncConfig,
                                         SyncLogStore syncLogStore) {
        init(store, System::currentTimeMillis, syncStore, pusher, puller,
                syncConfig, syncLogStore);
    }

    public static synchronized void init(EventLogStore store, TimeSource clock,
                                         SyncPointStore syncStore,
                                         EventLogPusher pusher,
                                         EventLogPuller puller,
                                         SyncConfig syncConfig,
                                         SyncLogStore syncLogStore) {
        INSTANCE = new EventLogClient(store, clock, syncStore, pusher, puller,
                syncConfig, syncLogStore);
    }

    public static synchronized void reset() {
        INSTANCE = null;
    }

    public EventLogStore store() {
        return store;
    }

    public SyncLogStore syncLogStore() {
        return syncLogStore;
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

    /**
     * Push locally-stored events for {@code topic} to the remote server,
     * advancing the push cursor on success. Per PROTOCOL §10.5 PUSH 流程:
     *
     * <ol>
     *   <li>Read push cursor from {@link SyncPointStore}.</li>
     *   <li>Query local events with {@code process_time > cursor}, batch size 100.</li>
     *   <li>POST the batch (§4.1.1). On non-2xx, return {@code -1}; cursor untouched.</li>
     *   <li>On 2xx, advance push cursor to {@code batch[-1].process_time}.</li>
     * </ol>
     *
     * <p>Threading: BLOCKING. Callers MUST run on a background thread.</p>
     *
     * @return number of events pushed on success; {@code -1} on failure
     */
    public int pushPending(String topic) {
        ensureSyncReady();
        String cursorKey = pushCursorKey(topic);
        String cursor = syncStore.get(cursorKey);
        List<EventRecord> batch = store.sinceByProcessTime(topic, cursor);
        if (batch.isEmpty()) {
            logSyncAttempt(SyncLogEntry.Direction.PUSH, 0, 0, null, true);
            return 0;
        }
        EventLogPusher.PushResult r;
        try {
            r = pusher.push(topic, batch);
        } catch (EventLogException e) {
            logSyncAttempt(SyncLogEntry.Direction.PUSH, batch.size(), 0,
                    e.getMessage(), false);
            throw e;
        }
        if (!r.success) {
            logSyncAttempt(SyncLogEntry.Direction.PUSH, r.sentCount, 0,
                    r.errorMessage, false);
            return -1;
        }
        syncStore.set(cursorKey, r.lastPushedProcessTime);
        logSyncAttempt(SyncLogEntry.Direction.PUSH, r.sentCount, 0, null, true);
        return r.sentCount;
    }

    /**
     * Pull events for {@code topic} from the remote server and persist them
     * locally, advancing the pull cursor on success. Per PROTOCOL §10.5 PULL 流程:
     *
     * <ol>
     *   <li>Read pull cursor from {@link SyncPointStore}.</li>
     *   <li>Loop: GET a batch (§4.2), append events to local store.</li>
     *   <li>Advance cursor to {@code batch[-1].process_time} after each batch.</li>
     *   <li>Stop when batch size < limit (signal: no more events).</li>
     * </ol>
     *
     * <p>LWW folding (§5.4) is NOT performed here — caller / sync layer is
     * responsible. This method only ensures remote events reach the local
     * append-only log.</p>
     *
     * <p>Threading: BLOCKING. Callers MUST run on a background thread.</p>
     *
     * @return total number of events pulled and persisted
     */
    public int pull(String topic) {
        ensureSyncReady();
        final int batchLimit = 1000;
        final int maxLoops = 100; // safety cap
        String cursorKey = pullCursorKey(topic);
        String cursor = syncStore.get(cursorKey);
        int total = 0;
        boolean allOk = true;
        String lastError = null;
        for (int i = 0; i < maxLoops; i++) {
            EventLogPuller.PullResult r;
            try {
                r = puller.pull(topic, cursor, batchLimit);
            } catch (EventLogException e) {
                allOk = false;
                lastError = e.getMessage();
                break;
            }
            if (!r.success) {
                allOk = false;
                lastError = r.errorMessage;
                break;
            }
            List<EventRecord> batch = r.events;
            if (batch.isEmpty()) {
                break;
            }
            for (EventRecord e : batch) {
                store.append(e);
                total++;
            }
            cursor = batch.get(batch.size() - 1).getProcessTime();
            syncStore.set(cursorKey, cursor);
            if (batch.size() < batchLimit) {
                break; // signal: server returned less than limit
            }
        }
        logSyncAttempt(SyncLogEntry.Direction.PULL, 0, total, lastError, allOk);
        return total;
    }

    private void logSyncAttempt(SyncLogEntry.Direction direction,
                                int sentCount, int receivedCount,
                                String errorMessage, boolean success) {
        if (syncLogStore == null) return;
        try {
            String ts = formatIso8601(clock.nowMillis());
            syncLogStore.add(direction, ts, success, sentCount, receivedCount, errorMessage);
        } catch (Exception ignored) {
            // best-effort: sync logging must never break sync itself
        }
    }

    private void ensureSyncReady() {
        if (syncStore == null || pusher == null || puller == null || syncConfig == null) {
            throw new EventLogException(
                    "EventLogClient sync not initialized; call init() with pusher/puller/syncConfig");
        }
    }

    private String pushCursorKey(String topic) {
        return "push:" + syncConfig.baseUrl() + ":" + topic;
    }

    private String pullCursorKey(String topic) {
        return "pull:" + syncConfig.baseUrl() + ":" + topic;
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
