package person.notfresh.readingshare.db;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import person.notfresh.readingshare.model.SearchHistoryItem;

/**
 * 搜索历史的纯逻辑层（无 Android 依赖，便于单元测试）
 * 三个职责：排序、upsert、LRU 淘汰
 */
public final class SearchHistoryLogic {

    private SearchHistoryLogic() {}

    /**
     * 排序：pinned 在前，组内按 lastUsed 降序
     */
    public static List<SearchHistoryItem> sortItems(List<SearchHistoryItem> items) {
        List<SearchHistoryItem> pinned = new ArrayList<>();
        List<SearchHistoryItem> unpinned = new ArrayList<>();
        for (SearchHistoryItem it : items) {
            if (it.isPinned()) pinned.add(it); else unpinned.add(it);
        }
        Comparator<SearchHistoryItem> byLastUsedDesc = (a, b) -> Long.compare(b.getLastUsed(), a.getLastUsed());
        pinned.sort(byLastUsedDesc);
        unpinned.sort(byLastUsedDesc);
        List<SearchHistoryItem> result = new ArrayList<>(items.size());
        result.addAll(pinned);
        result.addAll(unpinned);
        return result;
    }

    /**
     * upsert：keyword trim 后写入；若已存在则更新 lastUsed（保持原有 pinned 状态）
     * 输入为空（含纯空白）时静默返回
     */
    public static void upsertItem(List<SearchHistoryItem> items, String keyword, long now) {
        if (keyword == null) return;
        String trimmed = keyword.trim();
        if (trimmed.isEmpty()) return;

        for (SearchHistoryItem it : items) {
            if (it.getText().equals(trimmed)) {
                it.setLastUsed(now);
                return;
            }
        }
        items.add(new SearchHistoryItem(trimmed, false, now));
    }

    /**
     * LRU 淘汰：仅在 unpinned 数量 > maxCount 时淘汰最旧的
     * pinned 条目不参与计数，永不淘汰
     * 边界：等于 maxCount 时不淘汰（严格 >）
     */
    public static void evictLRU(List<SearchHistoryItem> items, int maxCount) {
        List<SearchHistoryItem> unpinned = new ArrayList<>();
        for (SearchHistoryItem it : items) {
            if (!it.isPinned()) unpinned.add(it);
        }
        if (unpinned.size() <= maxCount) return;

        unpinned.sort((a, b) -> Long.compare(a.getLastUsed(), b.getLastUsed()));  // 升序：最旧在前
        int toRemove = unpinned.size() - maxCount;
        // 收集要淘汰的 text
        java.util.Set<String> removeTexts = new java.util.HashSet<>();
        for (int i = 0; i < toRemove; i++) {
            removeTexts.add(unpinned.get(i).getText());
        }
        // 从原列表移除
        Iterator<SearchHistoryItem> it = items.iterator();
        while (it.hasNext()) {
            SearchHistoryItem item = it.next();
            if (!item.isPinned() && removeTexts.contains(item.getText())) {
                it.remove();
            }
        }
    }
}
