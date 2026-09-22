package person.notfresh.readingshare.eventlog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EventLogClientTest {

    private static final String DEVICE = "device-test-001";
    private static final long FIXED_MILLIS = 1_700_000_000_000L;

    private FakeStore store;
    private EventLogClient.TimeSource fixedClock;

    @Before
    public void setUp() {
        store = new FakeStore(DEVICE);
        fixedClock = () -> FIXED_MILLIS;
        EventLogClient.init(store, fixedClock);
    }

    @After
    public void tearDown() {
        EventLogClient.reset();
    }

    @Test
    public void create_returns_record_with_expected_fields() {
        EventRecord r = EventLogClient.get().create("links", "e1", "{\"a\":1}");

        assertEquals("links", r.getTopic());
        assertEquals("e1", r.getEntityId());
        assertEquals(EventAction.CREATE, r.getAction());
        assertEquals("{\"a\":1}", r.getData());
        assertEquals(DEVICE, r.getDeviceId());
        assertEquals("2023-11-14T22:13:20.000Z", r.getEventTime());
        assertEquals(r.getEventTime(), r.getProcessTime());
        assertNotNull(r.getId());
        assertEquals(16, r.getId().length());
    }

    @Test
    public void update_and_delete_carry_correct_action() {
        EventRecord u = EventLogClient.get().update("links", "e1", "{\"v\":2}");
        assertEquals(EventAction.UPDATE, u.getAction());
        assertEquals("{\"v\":2}", u.getData());

        EventRecord d = EventLogClient.get().delete("links", "e1");
        assertEquals(EventAction.DELETE, d.getAction());
        assertNull(d.getData());
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
    public void eventTime_is_iso8601_utc_with_z_suffix() {
        String s = EventLogClient.formatIso8601(FIXED_MILLIS);
        assertEquals("2023-11-14T22:13:20.000Z", s);
    }

    @Test
    public void empty_topic_throws() {
        try {
            EventLogClient.get().create("", "e1", "{}");
            fail("expected EventLogException");
        } catch (EventLogException expected) {
            assertEquals("topic is empty", expected.getMessage());
        }
    }

    @Test
    public void empty_entityId_throws() {
        try {
            EventLogClient.get().create("links", "", "{}");
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
        EventLogClient.get().create("links", "e1", "{}");
        EventLogClient.get().update("links", "e1", "{\"v\":2}");
        EventLogClient.get().create("other", "x", "{}");

        List<EventRecord> linkTail = EventLogClient.get().since("links", "");
        assertEquals(2, linkTail.size());

        List<EventRecord> noneTail = EventLogClient.get().since("nope", "");
        assertEquals(0, noneTail.size());
    }

    @Test
    public void since_with_limit_returns_at_most_n_records() {
        AtomicLong clock = new AtomicLong(FIXED_MILLIS);
        EventLogClient.init(store, clock::get);
        for (int i = 0; i < 5; i++) {
            clock.addAndGet(1);
            EventLogClient.get().create("links", "e" + i, "{}");
        }
        List<EventRecord> page = EventLogClient.get().since("links", "", 3);
        assertEquals(3, page.size());
    }

    @Test
    public void paging_walks_full_stream_in_order() {
        AtomicLong clock = new AtomicLong(FIXED_MILLIS);
        EventLogClient.init(store, clock::get);
        for (int i = 0; i < 7; i++) {
            clock.addAndGet(1);
            EventLogClient.get().create("links", "e" + i, "{}");
        }
        List<EventRecord> collected = new ArrayList<>();
        String cursor = "";
        while (true) {
            List<EventRecord> page = EventLogClient.get().since("links", cursor, 3);
            if (page.isEmpty()) break;
            collected.addAll(page);
            cursor = page.get(page.size() - 1).getProcessTime();
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

        EventRecord c = EventLogClient.get().create("links", "e1", "{}");
        assertEquals(c.getId(), EventLogClient.get().latest("links").getId());

        EventRecord u = EventLogClient.get().update("links", "e1", "{\"v\":2}");
        assertEquals(u.getId(), EventLogClient.get().latest("links").getId());
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
        public synchronized List<EventRecord> since(String topic, String sinceProcessTime) {
            return since(topic, sinceProcessTime, Integer.MAX_VALUE);
        }

        @Override
        public synchronized List<EventRecord> since(String topic, String sinceProcessTime, int limit) {
            if (limit <= 0) {
                throw new EventLogException("limit must be positive, got " + limit);
            }
            List<EventRecord> out = new ArrayList<>();
            for (EventRecord r : byId.values()) {
                if (!r.getTopic().equals(topic)) continue;
                if (r.getProcessTime().compareTo(sinceProcessTime) > 0) {
                    out.add(r);
                }
            }
            out.sort((a, b) -> a.getProcessTime().compareTo(b.getProcessTime()));
            if (out.size() > limit) {
                return new ArrayList<>(out.subList(0, limit));
            }
            return out;
        }

        @Override
        public synchronized EventRecord latest(String topic) {
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
        public String deviceId() {
            return deviceId;
        }
    }
}
