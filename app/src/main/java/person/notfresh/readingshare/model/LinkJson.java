package person.notfresh.readingshare.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
}
