package person.notfresh.readingshare.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 阅读页数据来源规格（值对象，持久化在 SharedPreferences "settings" / reader_source_json）。
 *
 * <p>单选一种来源（用户 2026-10 拍板）。{@code param} 只在 TAG / SUBJECT 时有意义：
 * TAG 存标签名，SUBJECT 存主题 id 的字符串形式。</p>
 *
 * <p>坏数据 / 缺字段一律降级为 ALL，不让配置污染阅读页（同 WebViewOverrideRule.fromJson 的范式）。</p>
 */
public final class ReaderSourceSpec {

    public static final String TYPE_ALL = "all";
    public static final String TYPE_PINNED = "pinned";
    public static final String TYPE_TAG = "tag";
    public static final String TYPE_SUBJECT = "subject";

    private final String type;
    private final String param;

    public ReaderSourceSpec(String type, String param) {
        this.type = (type == null || type.isEmpty()) ? TYPE_ALL : type;
        this.param = param == null ? "" : param;
    }

    public static ReaderSourceSpec all() {
        return new ReaderSourceSpec(TYPE_ALL, "");
    }

    public static ReaderSourceSpec pinned() {
        return new ReaderSourceSpec(TYPE_PINNED, "");
    }

    public static ReaderSourceSpec tag(String tagName) {
        return new ReaderSourceSpec(TYPE_TAG, tagName);
    }

    public static ReaderSourceSpec subject(long subjectId) {
        return new ReaderSourceSpec(TYPE_SUBJECT, String.valueOf(subjectId));
    }

    public String getType() {
        return type;
    }

    public String getParam() {
        return param;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("type", type);
        o.put("param", param);
        return o;
    }

    public static ReaderSourceSpec fromJson(JSONObject o) {
        if (o == null) return all();
        String type = o.optString("type", TYPE_ALL);
        String param = o.optString("param", "");
        if (!isKnownType(type)) return all();
        if ((TYPE_TAG.equals(type) || TYPE_SUBJECT.equals(type)) && param.isEmpty()) return all();
        return new ReaderSourceSpec(type, param);
    }

    public static boolean isKnownType(String type) {
        return TYPE_ALL.equals(type) || TYPE_PINNED.equals(type)
                || TYPE_TAG.equals(type) || TYPE_SUBJECT.equals(type);
    }
}
