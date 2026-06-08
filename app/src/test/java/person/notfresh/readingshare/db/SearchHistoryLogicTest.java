package person.notfresh.readingshare.db;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import person.notfresh.readingshare.model.SearchHistoryItem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SearchHistoryLogicTest {

    private static final long T1 = 1000L;
    private static final long T2 = 2000L;
    private static final long T3 = 3000L;

    // ===== sortItems =====

    @Test
    public void sortItems_pinnedBeforeUnpinned() {
        List<SearchHistoryItem> items = Arrays.asList(
            item("a", false, T2),
            item("b", true,  T1)
        );
        List<SearchHistoryItem> sorted = SearchHistoryLogic.sortItems(items);
        assertEquals("b", sorted.get(0).getText());
        assertEquals("a", sorted.get(1).getText());
    }

    @Test
    public void sortItems_eachGroupByLastUsedDesc() {
        List<SearchHistoryItem> items = Arrays.asList(
            item("p1", true,  T1),
            item("p2", true,  T3),
            item("u1", false, T2),
            item("u2", false, T3)
        );
        List<SearchHistoryItem> sorted = SearchHistoryLogic.sortItems(items);
        // pinned 组：p2 (T3) 在前, p1 (T1) 在后
        assertEquals("p2", sorted.get(0).getText());
        assertEquals("p1", sorted.get(1).getText());
        // unpinned 组：u2 (T3) 在前, u1 (T2) 在后
        assertEquals("u2", sorted.get(2).getText());
        assertEquals("u1", sorted.get(3).getText());
    }

    @Test
    public void sortItems_emptyList_returnsEmpty() {
        assertTrue(SearchHistoryLogic.sortItems(new ArrayList<>()).isEmpty());
    }

    // ===== upsertItem =====

    @Test
    public void upsertItem_newKeyword_appends() {
        List<SearchHistoryItem> items = new ArrayList<>();
        SearchHistoryLogic.upsertItem(items, "react", T1);
        assertEquals(1, items.size());
        assertEquals("react", items.get(0).getText());
        assertEquals(false, items.get(0).isPinned());
        assertEquals(T1, items.get(0).getLastUsed());
    }

    @Test
    public void upsertItem_existingUnpinned_updatesLastUsed() {
        List<SearchHistoryItem> items = new ArrayList<>(Arrays.asList(
            item("react", false, T1)
        ));
        SearchHistoryLogic.upsertItem(items, "react", T3);
        assertEquals(1, items.size());
        assertEquals(T3, items.get(0).getLastUsed());
    }

    @Test
    public void upsertItem_existingPinned_keepsPinnedAndUpdatesLastUsed() {
        List<SearchHistoryItem> items = new ArrayList<>(Arrays.asList(
            item("react", true, T1)
        ));
        SearchHistoryLogic.upsertItem(items, "react", T3);
        assertEquals(1, items.size());
        assertTrue(items.get(0).isPinned());
        assertEquals(T3, items.get(0).getLastUsed());
    }

    @Test
    public void upsertItem_trimsWhitespace() {
        List<SearchHistoryItem> items = new ArrayList<>();
        SearchHistoryLogic.upsertItem(items, "  react  ", T1);
        assertEquals("react", items.get(0).getText());
    }

    // ===== evictLRU =====

    @Test
    public void evictLRU_keepsAllPinned() {
        List<SearchHistoryItem> items = new ArrayList<>(Arrays.asList(
            item("p1", true,  T1),
            item("p2", true,  T2),
            item("u1", false, T1),
            item("u2", false, T2),
            item("u3", false, T3)
        ));
        // maxCount=2, 应保留 2 个最新 unpinned + 全部 pinned
        SearchHistoryLogic.evictLRU(items, 2);
        assertEquals(4, items.size());
        // 留下的 unpinned 应该是 T3 和 T2
        long uCount = items.stream().filter(it -> !it.isPinned()).count();
        long pCount = items.stream().filter(SearchHistoryItem::isPinned).count();
        assertEquals(2, uCount);
        assertEquals(2, pCount);
    }

    @Test
    public void evictLRU_dropsOldestUnpinnedWhenExceeds() {
        List<SearchHistoryItem> items = new ArrayList<>(Arrays.asList(
            item("u1", false, T1),
            item("u2", false, T2),
            item("u3", false, T3)
        ));
        // maxCount=2, u1 (T1 最旧) 应被淘汰（不验证顺序，那是 sortItems 的职责）
        SearchHistoryLogic.evictLRU(items, 2);
        assertEquals(2, items.size());
        java.util.Set<String> remaining = new java.util.HashSet<>();
        for (SearchHistoryItem it : items) remaining.add(it.getText());
        assertTrue("u1 (oldest) should be evicted", !remaining.contains("u1"));
        assertTrue("u2 should remain", remaining.contains("u2"));
        assertTrue("u3 should remain", remaining.contains("u3"));
    }

    @Test
    public void evictLRU_doesNotEvictAtMaxCountBoundary() {
        // 关键不变量：严格 > maxCount 才淘汰
        List<SearchHistoryItem> items = new ArrayList<>(Arrays.asList(
            item("u1", false, T1),
            item("u2", false, T2)
        ));
        SearchHistoryLogic.evictLRU(items, 2);  // 等于 maxCount
        assertEquals("边界等于 maxCount 时不淘汰", 2, items.size());
    }

    @Test
    public void evictLRU_onlyCountsUnpinned() {
        // pinned 不挤掉 unpinned 配额
        List<SearchHistoryItem> items = new ArrayList<>(Arrays.asList(
            item("p1", true,  T1),
            item("p2", true,  T2),
            item("p3", true,  T3),
            item("u1", false, T1)
        ));
        // maxCount=1 (unpinned 上限), 当前 unpinned=1 不超, 不应淘汰
        SearchHistoryLogic.evictLRU(items, 1);
        assertEquals(4, items.size());
    }

    // ===== helper =====

    private static SearchHistoryItem item(String text, boolean pinned, long ts) {
        return new SearchHistoryItem(text, pinned, ts);
    }
}
