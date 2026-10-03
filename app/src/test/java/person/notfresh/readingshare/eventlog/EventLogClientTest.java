package person.notfresh.readingshare.eventlog;

import android.content.SharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EventLogClientTest {

    private static final String DEVICE = "device-test-001";
    private static final long FIXED_MILLIS = 1_700_000_000_000L;
    private static final long FIXED_EVENT_MILLIS = 1_650_000_000_000L;

    private FakeStore store;
    private FakeSharedPreferences fakePrefs;
    private EventLogClient.TimeSource fixedClock;

    @Before
    public void setUp() {
        store = new FakeStore(DEVICE);
        fakePrefs = new FakeSharedPreferences();
        fixedClock = () -> FIXED_MILLIS;
        EventLogClient.init(store, fixedClock, fakePrefs);
    }

    @After
    public void tearDown() {
        EventLogClient.reset();
    }

    @Test
    public void create_returns_record_with_expected_fields() {
        EventRecord r = EventLogClient.get().create("links", "e1",
                FIXED_EVENT_MILLIS, "{\"a\":1}");

        assertEquals("links", r.getTopic());
        assertEquals("e1", r.getEntityId());
        assertEquals(EventAction.CREATE, r.getAction());
        assertEquals("{\"a\":1}", r.getData());
        assertEquals(DEVICE, r.getDeviceId());
        // event_time uses local timezone offset
        String expectedEvent = EventLogClient.formatLocalIso8601(FIXED_EVENT_MILLIS);
        assertEquals(expectedEvent, r.getEventTime());
        // process_time uses UTC
        String expectedProcess = EventLogClient.formatIso8601(FIXED_MILLIS);
        assertEquals(expectedProcess, r.getProcessTime());
        // event_time and process_time have different formats (local offset vs Z)
        assertNotEquals(r.getEventTime(), r.getProcessTime());
        assertNotNull(r.getId());
        assertEquals(16, r.getId().length());
    }

    @Test
    public void update_and_delete_carry_correct_action() {
        EventRecord u = EventLogClient.get().update("links", "e1",
                FIXED_EVENT_MILLIS, "{\"v\":2}");
        assertEquals(EventAction.UPDATE, u.getAction());
        assertEquals("{\"v\":2}", u.getData());

        EventRecord d = EventLogClient.get().delete("links", "e1",
                FIXED_MILLIS);
        assertEquals(EventAction.DELETE, d.getAction());
        assertNull(d.getData());
    }

    @Test
    public void old_carries_previous_state_create_has_none() {
        EventRecord c = EventLogClient.get().create("links", "e1",
                FIXED_EVENT_MILLIS, "{\"title\":\"v1\"}");
        assertNull("create 没有\"变更前\"", c.getOldJson());

        EventRecord u = EventLogClient.get().update("links", "e1",
                FIXED_EVENT_MILLIS, "{\"title\":\"v2\"}");
        assertEquals("update 的 old = 上一条 data", "{\"title\":\"v1\"}", u.getOldJson());

        EventRecord d = EventLogClient.get().delete("links", "e1", FIXED_MILLIS);
        assertEquals("delete 的 old = 被删前状态", "{\"title\":\"v2\"}", d.getOldJson());
        assertNull("delete 的 data 仍为 null", d.getData());
    }

    @Test
    public void untilByProcessTime_query_has_no_null_arg_on_first_page() {
        // 第一页 = UI 传 null 游标(EventLogActivity.loadNextPage)。
        // 2026-10-03 线上崩过:null 被直接塞进 SQL 参数,bindString 抛
        // IllegalArgumentException。此处锁死"参数里不允许出现 null"。
        SqliteEventLogStore.Query first =
                SqliteEventLogStore.buildUntilByProcessTimeQuery("links", null, 50);
        assertEquals(2, first.args.length);
        for (String a : first.args) {
            assertNotNull("SQL 参数不能是 null(第一页)", a);
        }
        assertFalse("第一页不该带上界条件", first.sql.contains("process_time <"));

        SqliteEventLogStore.Query next = SqliteEventLogStore.buildUntilByProcessTimeQuery(
                "links", "2026-01-01T00:00:00.000Z", 50);
        assertEquals(3, next.args.length);
        for (String a : next.args) {
            assertNotNull("SQL 参数不能是 null(翻页)", a);
        }
        assertTrue("翻页要带上界条件", next.sql.contains("process_time <"));
    }

    @Test
    public void id_is_sha256_of_topic_device_eventTime_entity_action_first_16_hex() {
        String topic = "links";
        String device = DEVICE;
        String eventTime = "2023-11-14T22:13:20.000Z";
        String entityId = "e1";
        String action = "CREATE";

        String expected = EventLogClient.computeId(topic, device, eventTime,
                entityId, action);

        assertEquals(16, expected.length());
        assertTrue(expected.matches("[0-9a-f]{16}"));

        String recomputed = EventLogClient.computeId(topic, device, eventTime,
                entityId, action);
        assertEquals(expected, recomputed);
    }

    @Test
    public void id_changes_when_any_input_changes() {
        String base = EventLogClient.computeId("links", DEVICE,
                "2023-11-14T22:13:20.000Z", "e1", "CREATE");
        assertTrue(!base.equals(EventLogClient.computeId("tags", DEVICE,
                "2023-11-14T22:13:20.000Z", "e1", "CREATE")));
        assertTrue(!base.equals(EventLogClient.computeId("links", "other",
                "2023-11-14T22:13:20.000Z", "e1", "CREATE")));
        assertTrue(!base.equals(EventLogClient.computeId("links", DEVICE,
                "2023-11-14T22:13:21.000Z", "e1", "CREATE")));
        assertTrue(!base.equals(EventLogClient.computeId("links", DEVICE,
                "2023-11-14T22:13:20.000Z", "e2", "CREATE")));
        assertTrue(!base.equals(EventLogClient.computeId("links", DEVICE,
                "2023-11-14T22:13:20.000Z", "e1", "UPDATE")));
    }

    @Test
    public void formatIso8601_utc_has_z_suffix() {
        String s = EventLogClient.formatIso8601(FIXED_MILLIS);
        assertTrue("should end with Z, got " + s, s.endsWith("Z"));
    }

    @Test
    public void formatLocalIso8601_has_offset_not_z() {
        // Force a known timezone so the test is reproducible
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            String s = EventLogClient.formatLocalIso8601(FIXED_MILLIS);
            assertTrue("should contain offset like +08:00, got " + s,
                    s.matches(".*[+-]\\d{2}:\\d{2}$"));
            assertTrue("should NOT end with Z, got " + s, !s.endsWith("Z"));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    public void eventTime_uses_caller_value_processTime_strictly_increases() {
        // Two calls with different eventTimeMillis but same store: event_time differs;
        // process_time 由 clock 分配且严格递增 — 同毫秒内第二条退化为 +1ms,
        // 保证推送游标(process_time > cursor)不会跳过同毫秒的后续事件
        EventRecord a = EventLogClient.get().create("links", "e1",
                1_000_000L, "{}");
        EventRecord b = EventLogClient.get().create("links", "e2",
                2_000_000L, "{}");

        assertNotEquals(a.getEventTime(), b.getEventTime());
        // 第一条取 clock 当前值
        assertEquals(EventLogClient.formatIso8601(FIXED_MILLIS), a.getProcessTime());
        // 第二条严格大于第一条(而非相同)
        assertTrue("process_time must strictly increase, got "
                        + a.getProcessTime() + " then " + b.getProcessTime(),
                b.getProcessTime().compareTo(a.getProcessTime()) > 0);
    }

    @Test
    public void empty_topic_throws() {
        try {
            EventLogClient.get().create("", "e1", FIXED_MILLIS, "{}");
            fail("expected EventLogException");
        } catch (EventLogException expected) {
            assertEquals("topic is empty", expected.getMessage());
        }
    }

    @Test
    public void empty_entityId_throws() {
        try {
            EventLogClient.get().create("links", "", FIXED_MILLIS, "{}");
            fail("expected EventLogException");
        } catch (EventLogException expected) {
            assertEquals("entityId is empty", expected.getMessage());
        }
    }

    @Test
    public void use_before_init_throws() {
        EventLogClient.reset();
        try {
            EventLogClient.get();
            fail("expected EventLogException");
        } catch (EventLogException expected) {
            assertTrue(expected.getMessage().contains("not initialized"));
        }
    }

    @Test
    public void bootstrap_flag_defaults_to_false() {
        assertEquals(false, EventLogClient.get().isBootstrapped());
    }

    @Test
    public void setBootstrapFlag_flips_to_true() {
        EventLogClient.get().setBootstrapFlag();
        assertEquals(true, EventLogClient.get().isBootstrapped());
    }

    @Test
    public void since_returns_repo_results() {
        EventLogClient.get().create("links", "e1", FIXED_MILLIS, "{}");
        EventLogClient.get().update("links", "e1", FIXED_MILLIS, "{\"v\":2}");
        EventLogClient.get().create("other", "x", FIXED_MILLIS, "{}");

        List<EventRecord> linkTail = EventLogClient.get().since("links", "");
        assertEquals(2, linkTail.size());

        List<EventRecord> noneTail = EventLogClient.get().since("nope", "");
        assertEquals(0, noneTail.size());
    }

    @Test
    public void since_with_limit_returns_at_most_n_records() {
        AtomicLong clock = new AtomicLong(FIXED_MILLIS);
        EventLogClient.init(store, clock::get, fakePrefs);
        for (int i = 0; i < 5; i++) {
            clock.addAndGet(1);
            EventLogClient.get().create("links", "e" + i, FIXED_MILLIS, "{}");
        }
        List<EventRecord> page = EventLogClient.get().since("links", "", 3);
        assertEquals(3, page.size());
    }

    @Test
    public void paging_walks_full_stream_in_order() {
        AtomicLong clock = new AtomicLong(FIXED_MILLIS);
        EventLogClient.init(store, clock::get, fakePrefs);
        for (int i = 0; i < 7; i++) {
            clock.addAndGet(1);
            EventLogClient.get().create("links", "e" + i, FIXED_MILLIS, "{}");
        }
        List<EventRecord> collected = new ArrayList<>();
        String cursor = "";
        while (true) {
            List<EventRecord> page = EventLogClient.get().since("links", cursor, 3);
            if (page.isEmpty()) break;
            collected.addAll(page);
            cursor = page.get(page.size() - 1).getEventTime();
            if (page.size() < 3) break;
        }
        assertEquals(7, collected.size());
        for (int i = 0; i < 7; i++) {
            assertEquals("e" + i, collected.get(i).getEntityId());
        }
    }

    @Test
    public void since_with_zero_limit_throws() {
        try {
            EventLogClient.get().since("links", "", 0);
            fail("expected EventLogException");
        } catch (EventLogException expected) {
            assertTrue(expected.getMessage().contains("limit"));
        }
    }

    @Test
    public void latest_returns_most_recent_or_null() {
        assertNull(EventLogClient.get().latest("links"));

        EventRecord c = EventLogClient.get().create("links", "e1",
                FIXED_MILLIS, "{}");
        assertEquals(c.getId(), EventLogClient.get().latest("links").getId());

        EventRecord u = EventLogClient.get().update("links", "e1",
                FIXED_MILLIS, "{\"v\":2}");
        assertEquals(u.getId(), EventLogClient.get().latest("links").getId());
    }

    /** 本地一落库就自动推送(防抖合并):连续两条事件只推一次,批次里两条都在。 */
    @Test
    public void record_triggers_debounced_auto_push() throws Exception {
        FakePusher pusher = new FakePusher();
        FakeSyncPoints points = new FakeSyncPoints();
        EventLogPuller puller = (topic, since, limit) ->
                EventLogPuller.PullResult.ok(new ArrayList<>());
        EventLogClient.init(store, points, pusher, puller,
                new SyncConfig("http://test.local", "s3cret"), null, null, fakePrefs);

        EventLogClient.get().create("links", "e1", FIXED_MILLIS, "{}");
        EventLogClient.get().update("links", "e1", FIXED_MILLIS, "{\"v\":2}");

        assertTrue("防抖窗口内应自动推一次", pusher.pushed.await(6, TimeUnit.SECONDS));
        assertEquals("两条事件应合并成一次推送", 1, pusher.batches.size());
        assertEquals(2, pusher.batches.get(0).size());
    }

    private static final class FakePusher implements EventLogPusher {
        final List<List<EventRecord>> batches = new ArrayList<>();
        final CountDownLatch pushed = new CountDownLatch(1);

        @Override
        public synchronized PushResult push(String topic, List<EventRecord> batch) {
            batches.add(new ArrayList<>(batch));
            pushed.countDown();
            return PushResult.ok(batch.size(), batch.size(), 0,
                    batch.get(batch.size() - 1).getProcessTime());
        }
    }

    private static final class FakeSyncPoints implements SyncPointStore {
        private final Map<String, String> map = new HashMap<>();

        @Override public String get(String key) { return map.get(key); }
        @Override public void set(String key, String value) { map.put(key, value); }
        @Override public void delete(String key) { map.remove(key); }
    }

    private static final class FakeStore implements EventLogStore {
        private final String deviceId;
        private final Map<String, EventRecord> byId = new HashMap<>();

        FakeStore(String deviceId) {
            this.deviceId = deviceId;
        }

        @Override
        public synchronized void append(EventRecord record) {
            byId.put(record.getId(), record);
        }

        @Override
        public synchronized List<EventRecord> since(String topic, String sinceEventTime) {
            return since(topic, sinceEventTime, Integer.MAX_VALUE);
        }

        @Override
        public synchronized List<EventRecord> since(String topic, String sinceEventTime, int limit) {
            if (limit <= 0) {
                throw new EventLogException("limit must be positive, got " + limit);
            }
            List<EventRecord> out = new ArrayList<>();
            String cutoff = sinceEventTime == null ? "" : sinceEventTime;
            for (EventRecord r : byId.values()) {
                if (!r.getTopic().equals(topic)) continue;
                if (r.getEventTime().compareTo(cutoff) > 0) {
                    out.add(r);
                }
            }
            out.sort((a, b) -> a.getEventTime().compareTo(b.getEventTime()));
            if (out.size() > limit) {
                return new ArrayList<>(out.subList(0, limit));
            }
            return out;
        }

        @Override
        public synchronized List<EventRecord> sinceByProcessTime(String topic, String sinceProcessTime, int limit) {
            List<EventRecord> out = new ArrayList<>();
            // 游标首次为 null —— 对齐生产 SqliteEventLogStore 的 null → "" 兜底
            String cutoff = sinceProcessTime == null ? "" : sinceProcessTime;
            for (EventRecord r : byId.values()) {
                if (!r.getTopic().equals(topic)) continue;
                if (r.getProcessTime().compareTo(cutoff) > 0) {
                    out.add(r);
                }
            }
            out.sort((a, b) -> a.getProcessTime().compareTo(b.getProcessTime())); // ASC
            if (out.size() > limit) {
                return new ArrayList<>(out.subList(0, limit));
            }
            return out;
        }

        @Override
        public synchronized List<EventRecord> until(String topic, String untilEventTime, int limit) {
            if (limit <= 0) {
                throw new EventLogException("limit must be positive, got " + limit);
            }
            List<EventRecord> out = new ArrayList<>();
            for (EventRecord r : byId.values()) {
                if (!r.getTopic().equals(topic)) continue;
                if (untilEventTime == null || r.getEventTime().compareTo(untilEventTime) < 0) {
                    out.add(r);
                }
            }
            out.sort((a, b) -> b.getEventTime().compareTo(a.getEventTime())); // DESC
            if (out.size() > limit) {
                return new ArrayList<>(out.subList(0, limit));
            }
            return out;
        }

        @Override
        public synchronized List<EventRecord> untilByProcessTime(String topic, String untilProcessTime, int limit) {
            if (limit <= 0) {
                throw new EventLogException("limit must be positive, got " + limit);
            }
            List<EventRecord> out = new ArrayList<>();
            for (EventRecord r : byId.values()) {
                if (!r.getTopic().equals(topic)) continue;
                if (untilProcessTime == null || r.getProcessTime().compareTo(untilProcessTime) < 0) {
                    out.add(r);
                }
            }
            out.sort((a, b) -> b.getProcessTime().compareTo(a.getProcessTime())); // DESC
            if (out.size() > limit) {
                return new ArrayList<>(out.subList(0, limit));
            }
            return out;
        }

        @Override
        public synchronized String latestDataJson(String topic, String entityId) {
            EventRecord best = null;
            for (EventRecord r : byId.values()) {
                if (!r.getTopic().equals(topic) || !r.getEntityId().equals(entityId)) continue;
                if (r.getData() == null) continue;
                if (best == null || r.getProcessTime().compareTo(best.getProcessTime()) > 0) {
                    best = r;
                }
            }
            return best == null ? null : best.getData();
        }

        @Override
        public EventRecord latest(String topic) {
            EventRecord best = null;
            for (EventRecord r : byId.values()) {
                if (!r.getTopic().equals(topic)) continue;
                if (best == null || r.getProcessTime().compareTo(best.getProcessTime()) > 0) {
                    best = r;
                }
            }
            return best;
        }

        @Override
        public int count(String topic) {
            int c = 0;
            for (EventRecord r : byId.values()) {
                if (r.getTopic().equals(topic)) c++;
            }
            return c;
        }

        @Override
        public synchronized void deleteAll() {
            byId.clear();
        }

        @Override
        public String deviceId() {
            return deviceId;
        }
    }

    /**
     * 内存版 SharedPreferences — 测 EventLogClient 的 bootstrap 标志位持久化接口契约。
     * 用 Map<String, Object> 存值;实现 Android 接口的全部 ~22 个方法,大部分抛
     * UnsupportedOperationException(测试不调用)。
     */
    private static class FakeSharedPreferences implements SharedPreferences {
        private final Map<String, Object> map = new HashMap<>();

        @Override public Map<String, ?> getAll() { return new HashMap<>(map); }
        @Override public String getString(String k, String d) {
            Object v = map.get(k); return v instanceof String ? (String) v : d;
        }
        @Override public Set<String> getStringSet(String k, Set<String> d) {
            Object v = map.get(k); return v instanceof Set ? (Set<String>) v : d;
        }
        @Override public int getInt(String k, int d) {
            Object v = map.get(k); return v instanceof Integer ? (Integer) v : d;
        }
        @Override public long getLong(String k, long d) {
            Object v = map.get(k); return v instanceof Long ? (Long) v : d;
        }
        @Override public float getFloat(String k, float d) {
            Object v = map.get(k); return v instanceof Float ? (Float) v : d;
        }
        @Override public boolean getBoolean(String k, boolean d) {
            Object v = map.get(k); return v instanceof Boolean ? (Boolean) v : d;
        }
        @Override public boolean contains(String k) { return map.containsKey(k); }
        @Override public Editor edit() { return new FakeEditor(); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}

        private class FakeEditor implements Editor {
            private final Map<String, Object> pending = new HashMap<>();
            private boolean clearAll = false;

            @Override public Editor putString(String k, String v) { pending.put(k, v); return this; }
            @Override public Editor putStringSet(String k, Set<String> v) { pending.put(k, v); return this; }
            @Override public Editor putInt(String k, int v) { pending.put(k, v); return this; }
            @Override public Editor putLong(String k, long v) { pending.put(k, v); return this; }
            @Override public Editor putFloat(String k, float v) { pending.put(k, v); return this; }
            @Override public Editor putBoolean(String k, boolean v) { pending.put(k, v); return this; }
            @Override public Editor remove(String k) { pending.put(k, null); return this; }
            @Override public Editor clear() { clearAll = true; return this; }

            @Override public boolean commit() { apply(); return true; }

            @Override public void apply() {
                if (clearAll) map.clear();
                for (Map.Entry<String, Object> e : pending.entrySet()) {
                    if (e.getValue() == null) map.remove(e.getKey());
                    else map.put(e.getKey(), e.getValue());
                }
            }
        }
    }
}
