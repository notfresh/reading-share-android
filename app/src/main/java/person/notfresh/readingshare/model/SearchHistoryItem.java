package person.notfresh.readingshare.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/**
 * 搜索历史条目
 * 字段：text（trim 后存的搜索词）、pinned（是否固定）、lastUsed（毫秒时间戳）
 */
public class SearchHistoryItem {
    private String text;
    private boolean pinned;
    private long lastUsed;

    public SearchHistoryItem(String text, boolean pinned, long lastUsed) {
        this.text = text;
        this.pinned = pinned;
        this.lastUsed = lastUsed;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }

    public long getLastUsed() { return lastUsed; }
    public void setLastUsed(long lastUsed) { this.lastUsed = lastUsed; }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("text", text);
        obj.put("pinned", pinned);
        obj.put("lastUsed", lastUsed);
        return obj;
    }

    public static SearchHistoryItem fromJson(JSONObject obj) throws JSONException {
        return new SearchHistoryItem(
            obj.getString("text"),
            obj.getBoolean("pinned"),
            obj.getLong("lastUsed")
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SearchHistoryItem)) return false;
        SearchHistoryItem that = (SearchHistoryItem) o;
        return pinned == that.pinned
            && lastUsed == that.lastUsed
            && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, pinned, lastUsed);
    }
}
