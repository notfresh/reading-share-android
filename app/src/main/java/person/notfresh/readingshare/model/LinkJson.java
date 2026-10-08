package person.notfresh.readingshare.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class LinkJson {

    private LinkJson() {}

    public static String toJsonString(LinkItem item) {
        if (item == null) return null;
        try {
            JSONObject o = new JSONObject();
            o.put("id", item.getId());
            o.put("title", item.getTitle());
            o.put("url", item.getUrl());
            o.put("remark", item.getRemark());
            o.put("source_app", item.getSourceApp());
            o.put("timestamp", item.getTimestamp());
            o.put("original_intent", item.getOriginalIntent());
            o.put("target_activity", item.getTargetActivity());
            o.put("is_pinned", item.isPinned());
            o.put("summary", item.getSummary());
            o.put("click_count", item.getClickCount());
            JSONArray tags = new JSONArray();
            if (item.getTags() != null) {
                for (String t : item.getTags()) {
                    tags.put(t);
                }
            }
            o.put("tags", tags);
            return o.toString();
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * 反序列化 — 配合服务端拉到的事件 data 反向还原 LinkItem。
     * tags 缺失时给空 list；click_count / is_pinned 等可选字段缺失时给默认值。
     */
    public static LinkItem fromJsonString(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            JSONObject o = new JSONObject(json);
            long id = o.optLong("id", 0L);
            String title = o.optString("title", "");
            String url = o.optString("url", "");
            String remark = o.optString("remark", "");
            String sourceApp = o.optString("source_app", "");
            long timestamp = o.optLong("timestamp", System.currentTimeMillis());
            String originalIntent = o.optString("original_intent", "");
            String targetActivity = o.optString("target_activity", "");
            boolean isPinned = o.optBoolean("is_pinned", false);
            String summary = o.optString("summary", "");
            int clickCount = o.optInt("click_count", 0);
            List<String> tags = new ArrayList<>();
            JSONArray tagsArr = o.optJSONArray("tags");
            if (tagsArr != null) {
                for (int i = 0; i < tagsArr.length(); i++) {
                    tags.add(tagsArr.optString(i, ""));
                }
            }
            LinkItem item = new LinkItem(title, url, sourceApp, originalIntent, targetActivity);
            item.setId(id);
            item.setTimestamp(timestamp);
            item.setPinned(isPinned);
            item.setSummary(summary);
            item.setRemark(remark);
            item.setClickCount(clickCount);
            item.setTags(tags);
            return item;
        } catch (JSONException e) {
            return null;
        }
    }
}
