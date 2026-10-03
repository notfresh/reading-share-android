package person.notfresh.readingshare.eventlog;

import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EventLogClient {

    private static volatile EventLogClient INSTANCE;

    private final EventLogStore store;
    private final TimeSource clock;
    private final SyncPointStore syncStore;
    private final EventLogPusher pusher;
    private final EventLogPuller puller;
    private final SyncConfig syncConfig;
    private final SyncLogStore syncLogStore;
    private final LinkApplier linkApplier;
    private final SharedPreferences bootstrapPrefs;

    private static final String BOOTSTRAP_DONE_KEY = "bootstrap_done";

    /**
     * 推送单批条数 — 与协议 §4.1.1 服务端单次 POST 上限 1000 一致。
     * 超过则由 {@link #pushPending} 循环分批推完。
     */
    private static final int PUSH_BATCH_LIMIT = 1000;

    /** 最近一次分配的 process_time 毫秒值 — 保证同 topic 内严格递增。 */
    private long lastProcessMillis = -1L;

    /**
     * 本地一产生事件就自动推送：安静 {@link #AUTO_PUSH_DEBOUNCE_MS} 毫秒后推一批。
     * 单线程 + 单次排队({@link #autoPushScheduled}) 保证：① 不并发推；
     * ② 一次批量操作(导入 / 灌历史)只触发一次推送，而不是每条一个 HTTP 请求。
     */
    private static final long AUTO_PUSH_DEBOUNCE_MS = 1500L;
    private final ScheduledExecutorService autoPushExecutor;
    private final AtomicBoolean autoPushScheduled = new AtomicBoolean(false);

    private EventLogClient(EventLogStore store, TimeSource clock,
                           SyncPointStore syncStore,
                           EventLogPusher pusher,
                           EventLogPuller puller,
                           SyncConfig syncConfig,
                           SyncLogStore syncLogStore,
                           LinkApplier linkApplier,
                           SharedPreferences bootstrapPrefs) {
        this.store = store;
        this.clock = clock;
        this.syncStore = syncStore;
        this.pusher = pusher;
        this.puller = puller;
        this.syncConfig = syncConfig;
        this.syncLogStore = syncLogStore;
        this.linkApplier = linkApplier;
        this.bootstrapPrefs = bootstrapPrefs;
        this.autoPushExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "eventlog-auto-push");
            t.setDaemon(true);
            return t;
        });
    }

    public static EventLogClient get() {
        EventLogClient c = INSTANCE;
        if (c == null) {
            throw new EventLogException(
                    "EventLogClient not initialized; call init() first");
        }
        return c;
    }

    public static synchronized void init(EventLogStore store, SharedPreferences bootstrapPrefs) {
        init(store, System::currentTimeMillis, null, null, null, null, null,
                NOOP_LINK_APPLIER, bootstrapPrefs);
    }

    /** 单 store + applier init — 用于无服务端同步场景但仍要折叠本地事件 */
    public static synchronized void init(EventLogStore store, LinkApplier linkApplier,
                                         SharedPreferences bootstrapPrefs) {
        init(store, System::currentTimeMillis, null, null, null, null, null,
                linkApplier == null ? NOOP_LINK_APPLIER : linkApplier, bootstrapPrefs);
    }

    public static synchronized void init(EventLogStore store, TimeSource clock,
                                         SharedPreferences bootstrapPrefs) {
        init(store, clock, null, null, null, null, null, NOOP_LINK_APPLIER, bootstrapPrefs);
    }

    /**
     * Full init — wires up remote sync + LWW folding. Pass {@code null} for any of
     * {@code syncStore} / {@code pusher} / {@code puller} / {@code syncConfig} /
     * {@code syncLogStore} to keep that subsystem disabled. {@code pushPending}
     * / {@code pull} will then throw {@link EventLogException} when called.
     * {@code linkApplier} folds pulled events back into business tables; pass
     * a real impl (e.g. {@code LinkEventApplier}) for the links topic.
     * {@code bootstrapPrefs} persists the "本地 link 已灌进 events 表" 标志位 —
     * 生产传 {@code getSharedPreferences("eventlog_bootstrap_prefs", MODE_PRIVATE)},
     * 测试传 FakeSharedPreferences。
     */
    public static synchronized void init(EventLogStore store,
                                         SyncPointStore syncStore,
                                         EventLogPusher pusher,
                                         EventLogPuller puller,
                                         SyncConfig syncConfig,
                                         SyncLogStore syncLogStore,
                                         LinkApplier linkApplier,
                                         SharedPreferences bootstrapPrefs) {
        init(store, System::currentTimeMillis, syncStore, pusher, puller,
                syncConfig, syncLogStore, linkApplier, bootstrapPrefs);
    }

    public static synchronized void init(EventLogStore store, TimeSource clock,
                                         SyncPointStore syncStore,
                                         EventLogPusher pusher,
                                         EventLogPuller puller,
                                         SyncConfig syncConfig,
                                         SyncLogStore syncLogStore,
                                         LinkApplier linkApplier,
                                         SharedPreferences bootstrapPrefs) {
        INSTANCE = new EventLogClient(store, clock, syncStore, pusher, puller,
                syncConfig, syncLogStore,
                linkApplier == null ? NOOP_LINK_APPLIER : linkApplier,
                bootstrapPrefs);
    }

    /** 单向门警告 — 此 fallback 仅给老 init 路径用，业务方必须显式注入。 */
    private static final LinkApplier NOOP_LINK_APPLIER = event -> { /* noop */ };

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
        return bootstrapPrefs.getBoolean(BOOTSTRAP_DONE_KEY, false);
    }

    public void setBootstrapFlag() {
        bootstrapPrefs.edit().putBoolean(BOOTSTRAP_DONE_KEY, true).apply();
    }

    public void resetBootstrapFlag() {
        bootstrapPrefs.edit().putBoolean(BOOTSTRAP_DONE_KEY, false).apply();
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

    /**
     * 按 process_time(日志写入时刻)倒序翻页 —— 事件日志 UI 的"写入序"展示。
     *
     * <p>与 {@link #until} 的区别: {@code until} 按 event_time 排,而 event_time 是
     * 各埋点自己传的"实体时间"(有的传 now(),有的传 link.getTimestamp() =
     * 链接创建时间),同一屏里新旧混排,看起来顺序是乱的。
     * process_time 由 {@link #nextProcessTime()} 保证同 topic 内严格递增,是真正的写入序。</p>
     */
    public List<EventRecord> untilByProcessTime(String topic, String untilProcessTime, int limit) {
        return store.untilByProcessTime(topic, untilProcessTime, limit);
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
        // 协议 §10.5 PUSH: 每批 ≤ BATCH_LIMIT(store 层默认 100 条),
        // 客户端循环取批直到取空 — 上千条待推事件不再只推第一批。
        final int maxLoops = 10000; // 安全上限,防游标卡死空转
        int totalSent = 0;
        boolean allOk = true;
        String lastError = null;
        for (int i = 0; i < maxLoops; i++) {
            List<EventRecord> batch = store.sinceByProcessTime(topic, cursor, PUSH_BATCH_LIMIT);
            if (batch.isEmpty()) break;
            EventLogPusher.PushResult r;
            try {
                r = pusher.push(topic, batch);
            } catch (EventLogException e) {
                logSyncAttempt(SyncLogEntry.Direction.PUSH, totalSent, 0,
                        e.getMessage(), false);
                throw e;
            }
            if (!r.success) {
                allOk = false;
                lastError = r.errorMessage;
                break;
            }
            syncStore.set(cursorKey, r.lastPushedProcessTime);
            cursor = r.lastPushedProcessTime;
            totalSent += r.sentCount;
        }
        logSyncAttempt(SyncLogEntry.Direction.PUSH, totalSent, 0, lastError, allOk);
        return allOk ? totalSent : -1;
    }

    /**
     * 事件刚落库 → 安排一次自动推送(防抖合并)。
     *
     * <p>为什么防抖:导入 1000 条 / bootstrap 灌 3000 条时,若每条一个 POST 就是
     * 上千次请求;合并成"安静一会儿后推一批"后,单条用户操作 ≈ 一次请求。</p>
     *
     * <p>失败不在这里处理 —— 推送游标不推进 = 事件仍在本地,由下一次推送
     * (或启动同步 / 手动同步)重试,天然 at-least-once 幂等。</p>
     *
     * <p>未配置同步(无 pusher/syncConfig)时静默跳过。</p>
     */
    private void scheduleAutoPush(String topic) {
        if (syncStore == null || pusher == null || puller == null || syncConfig == null) {
            return; // 未配置同步:静默跳过,不能因为没填 URL 就出问题
        }
        if (!autoPushScheduled.compareAndSet(false, true)) {
            return; // 已有一次排在队里 → 合并掉,不重复排
        }
        try {
            autoPushExecutor.schedule(() -> {
                autoPushScheduled.set(false);
                try {
                    pushPending(topic);
                } catch (Exception ignored) {
                    // best-effort:失败已写进 sync_log,事件仍在本地等下次
                }
            }, AUTO_PUSH_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            autoPushScheduled.set(false); // 调度器异常时别把标志卡死
        }
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
            } catch (Exception e) {
                // 捕获所有异常（不只是 EventLogException），确保 sync_log 一定写
                // — 否则 pull 异常时 recent(1) 只显示 push 那条，pull 失败被吞掉看不见
                allOk = false;
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
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
                // LWW folding — pull 到的事件折叠回 link 表（PROTOCOL §5.4）
                // 折叠是 sync layer 职责，本部署里 EventLogClient 兼任；
                // applier 决定哪些 topic 折叠（link 业务折叠，其它 noop）
                linkApplier.apply(e);
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

    /**
     * 分配一个单调递增的 process_time(UTC ISO-8601,毫秒精度)。
     *
     * <p>同一毫秒内连续写入多条事件时,后一条退化为「上一条 +1ms」——保证
     * 同 topic 内 process_time <b>严格递增</b>。否则推送游标按
     * {@code process_time > cursor} 推进时,会把与游标同毫秒的剩余事件整批跳过
     * (例如一次导入 3000 条 link 全落在同一毫秒)。</p>
     *
     * <p>代价:时间戳最多向前漂移 (同毫秒事件数) 毫秒;process_time 只作游标,
     * 不参与业务语义,漂移无害。</p>
     */
    private synchronized String nextProcessTime() {
        long ms = clock.nowMillis();
        if (ms <= lastProcessMillis) {
            ms = lastProcessMillis + 1;
        }
        lastProcessMillis = ms;
        return formatIso8601(ms);
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
        // process_time: 日志生成时刻,UTC(ISO-8601 + Z),同 topic 内严格递增
        String processTime = nextProcessTime();
        String deviceId = store.deviceId();
        // id 必须用「线上协议值」算 —— PROTOCOL §3.1 的 action 枚举是
        // create|update|delete(小写),服务端按收到的小写 action 重算同一个 id。
        // 用 EventAction.name() 的 "DELETE" 大写会算出一套不同的 id,导致:
        //   1) 推送的 id 与服务端存储的 id 不匹配(曾表现为 400 id_mismatch);
        //   2) 拉取落库(INSERT OR IGNORE 按 id 去重)永远去不掉重 ——
        //      远程事件在本地日志里重复写一行,折叠时还会把删掉的 link 折回来。
        String id = computeId(topic, deviceId, eventTime, entityId,
                action.name().toLowerCase(Locale.US));
        // §3.1: old = 本条事件生效前的实体状态(供 diff 用)。create 没有"之前" → null。
        // 从本地事件流取该实体上一条带 data 的事件 —— 与折叠(LWW)同源,
        // 所以 26 个埋点不需要各自传快照。
        String oldJson = action == EventAction.CREATE
                ? null
                : store.latestDataJson(topic, entityId);
        EventRecord r = new EventRecord(id, topic, processTime, eventTime,
                deviceId, entityId, action, dataJson, oldJson);
        store.append(r);
        // 本地一产生事件就排队推送(防抖合并) —— 让另一台设备尽快看到这个变更
        scheduleAutoPush(topic);
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
