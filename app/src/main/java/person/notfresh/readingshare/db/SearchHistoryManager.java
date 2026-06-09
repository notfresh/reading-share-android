package person.notfresh.readingshare.db;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import person.notfresh.readingshare.model.SearchHistoryItem;

/**
 * 搜索历史持久化层
 * 存储后端：SharedPreferences（key="searchHistory"，value=JSON）
 * 数据结构：{ items: [{text, pinned, lastUsed}, ...], maxCount: int }
 */
public class SearchHistoryManager {

    private static final String PREFS_NAME = "search_history_prefs";
    private static final String KEY_DATA = "searchHistory";
    private static final int DEFAULT_MAX_COUNT = 10;
    private static final String FIELD_ITEMS = "items";
    private static final String FIELD_MAX_COUNT = "maxCount";

    private final SharedPreferences prefs;

    public SearchHistoryManager(Context context) {
        this.prefs = context.getApplicationContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** 读取并按 sortItems 排序后返回 */
    public List<SearchHistoryItem> loadHistory() {
        Data data = readData();
        return SearchHistoryLogic.sortItems(data.items);
    }

    /** 写入：trim → upsert → 淘汰 → save */
    public void addSearchKeyword(String keyword) {
        if (keyword == null) return;
        String trimmed = keyword.trim();
        if (trimmed.isEmpty()) return;

        Data data = readData();
        SearchHistoryLogic.upsertItem(data.items, trimmed, System.currentTimeMillis());
        SearchHistoryLogic.evictLRU(data.items, data.maxCount);
        writeData(data);
    }

    /** 翻转 pinned 状态后保存（若不存在则不操作） */
    public void togglePinKeyword(String keyword) {
        if (keyword == null) return;
        Data data = readData();
        boolean changed = false;
        for (SearchHistoryItem it : data.items) {
            if (it.getText().equals(keyword)) {
                it.setPinned(!it.isPinned());
                changed = true;
                break;
            }
        }
        if (changed) writeData(data);
    }

    /** 删除后保存 */
    public void deleteKeyword(String keyword) {
        if (keyword == null) return;
        Data data = readData();
        boolean changed = data.items.removeIf(it -> it.getText().equals(keyword));
        if (changed) writeData(data);
    }

    /** 设置上限，立即按 LRU 淘汰多余（仅 unpinned） */
    public void setMaxCount(int count) {
        if (count < 0) count = 0;
        Data data = readData();
        data.maxCount = count;
        SearchHistoryLogic.evictLRU(data.items, count);
        writeData(data);
    }

    public int getMaxCount() {
        return readData().maxCount;
    }

    // ===== 内部：序列化/反序列化 =====

    private static class Data {
        List<SearchHistoryItem> items = new ArrayList<>();
        int maxCount = DEFAULT_MAX_COUNT;
    }

    private Data readData() {
        Data data = new Data();
        String raw = prefs.getString(KEY_DATA, null);
        if (raw == null) return data;
        try {
            JSONObject root = new JSONObject(raw);
            data.maxCount = root.optInt(FIELD_MAX_COUNT, DEFAULT_MAX_COUNT);
            JSONArray arr = root.optJSONArray(FIELD_ITEMS);
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    data.items.add(SearchHistoryItem.fromJson(obj));
                }
            }
        } catch (JSONException e) {
            // 损坏的数据：返回空数据，不抛异常
        }
        return data;
    }

    private void writeData(Data data) {
        try {
            JSONObject root = new JSONObject();
            root.put(FIELD_MAX_COUNT, data.maxCount);
            JSONArray arr = new JSONArray();
            for (SearchHistoryItem it : data.items) {
                arr.put(it.toJson());
            }
            root.put(FIELD_ITEMS, arr);
            prefs.edit().putString(KEY_DATA, root.toString()).apply();
        } catch (JSONException e) {
            // 写入失败：静默，不影响 UI
        }
    }
}
