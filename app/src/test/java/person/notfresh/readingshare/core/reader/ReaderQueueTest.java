package person.notfresh.readingshare.core.reader;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** ReaderQueue 的内核契约：分页取数、边界补页、窗口裁剪、环形回绕。 */
public class ReaderQueueTest {

    private static final class ListSource implements ReaderSource {
        private final List<Long> ids;
        private final List<Integer> requestOffsets = new ArrayList<>();

        ListSource(int count) {
            ids = new ArrayList<>(count);
            for (int i = 0; i < count; i++) ids.add((long) i);
        }

        @Override
        public String type() {
            return "fake";
        }

        @Override
        public List<Long> loadPage(int offset, int limit) {
            requestOffsets.add(offset);
            if (offset < 0 || offset >= ids.size()) return new ArrayList<>();
            return new ArrayList<>(ids.subList(offset, Math.min(ids.size(), offset + limit)));
        }
    }

    @Test
    public void load_takesFirstPageAndStartsAtFirstItem() {
        ListSource source = new ListSource(120);
        ReaderQueue queue = new ReaderQueue(source);

        assertTrue(queue.load());
        assertEquals(Long.valueOf(0L), queue.current());
        assertEquals(ReaderQueue.PAGE_SIZE, queue.size());
        assertEquals(0, queue.windowStart());
    }

    @Test
    public void next_crossesPageBoundaryByLoadingNextPage() {
        ListSource source = new ListSource(120);
        ReaderQueue queue = new ReaderQueue(source);
        queue.load();

        for (int i = 1; i < ReaderQueue.PAGE_SIZE; i++) {
            queue.next();
        }
        assertEquals(Long.valueOf(49L), queue.current());

        Long afterBoundary = queue.next();
        assertEquals(Long.valueOf(50L), afterBoundary);
        assertTrue("第二页应已加载", queue.size() > ReaderQueue.PAGE_SIZE);
    }

    @Test
    public void next_wrapsToFirstWhenSourceExhausted() {
        ListSource source = new ListSource(3);
        ReaderQueue queue = new ReaderQueue(source);
        queue.load();

        queue.next();
        queue.next();
        assertEquals(Long.valueOf(2L), queue.current());
        assertEquals("末尾下一条应回绕", Long.valueOf(0L), queue.next());
    }

    @Test
    public void previous_wrapsWithinLoadedWindowAtStart() {
        ListSource source = new ListSource(120);
        ReaderQueue queue = new ReaderQueue(source);
        queue.load();

        Long wrapped = queue.previous();
        assertEquals("开头上一条回绕到已加载窗口末尾", Long.valueOf(ReaderQueue.PAGE_SIZE - 1L), wrapped);
    }

    @Test
    public void longForwardScrollingTrimsWindowAndKeepsCursorOnSameItem() {
        ListSource source = new ListSource(600);
        ReaderQueue queue = new ReaderQueue(source);
        queue.load();

        int steps = ReaderQueue.MAX_WINDOW + 60;
        Long last = queue.current();
        for (int i = 0; i < steps; i++) {
            last = queue.next();
        }

        assertEquals(Long.valueOf(steps), last);
        assertTrue("窗口不应超过上限", queue.size() <= ReaderQueue.MAX_WINDOW);
        assertTrue("窗口起点应随裁剪前移", queue.windowStart() > 0);
    }

    @Test
    public void previous_loadsEarlierPageInsteadOfWrapping() {
        ListSource source = new ListSource(600);
        ReaderQueue queue = new ReaderQueue(source);
        queue.load();

        int forward = ReaderQueue.MAX_WINDOW + 20;
        for (int i = 0; i < forward; i++) queue.next();
        int windowStartBefore = queue.windowStart();
        assertTrue(windowStartBefore > 0);

        // 退回窗口头部(期间不会触发补页,窗口起点保持不变)
        while (queue.index() > 0) queue.previous();
        assertEquals(0, queue.index());
        assertEquals(windowStartBefore, queue.windowStart());

        Long previous = queue.previous();
        assertNotNull(previous);
        assertTrue("应向更早的一页补页,窗口起点前移", queue.windowStart() < windowStartBefore);
        assertEquals("补页后应落在旧窗口起点的前一条", Long.valueOf(windowStartBefore - 1L), previous);
    }

    @Test
    public void random_neverReturnsCurrentItem() {
        ListSource source = new ListSource(5);
        ReaderQueue queue = new ReaderQueue(source, new Random(42));
        queue.load();

        for (int i = 0; i < 50; i++) {
            Long before = queue.current();
            Long picked = queue.random();
            assertNotNull(picked);
            assertFalse("随机不应返回当前项", picked.equals(before));
        }
    }

    @Test
    public void emptySource_reportsEmptyAndStaysEmpty() {
        ListSource source = new ListSource(0);
        ReaderQueue queue = new ReaderQueue(source);

        assertFalse(queue.load());
        assertTrue(queue.isEmpty());
        assertNull(queue.current());
        assertNull(queue.next());
        assertNull(queue.previous());
        assertNull(queue.random());
    }
}
