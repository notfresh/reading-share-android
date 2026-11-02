package person.notfresh.readingshare.links;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import person.notfresh.readingshare.model.LinkItem;

/**
 * 跨端同步的 wire 格式 —— 协议层的规范字段格式。
 *
 * <p><b>为什么需要这一层</b>：两端本地存储的字段类型不一样，但协议层必须
 * 统一 —— 否则字段级 LWW 的 diff 会一直误判「字段改了」（同一个值，两端
 * 表示不同 → 每次 diff 都算改动 → 每次都覆盖）。</p>
 *
 * <table border="1">
 *   <caption>跨端 wire 格式对照</caption>
 *   <tr><th>字段</th><th>协议层</th><th>插件本地</th><th>Android 本地</th></tr>
 *   <tr><td>title</td><td>String</td><td>String</td><td>String</td></tr>
 *   <tr><td>url</td><td>String</td><td>String</td><td>String</td></tr>
 *   <tr><td>tags</td><td>String[]</td><td>"a,b" 逗号串</td><td>List&lt;String&gt;</td></tr>
 *   <tr><td>timestamp</td><td>long (epoch ms)</td><td>ISO-8601 字符串</td><td>long 毫秒</td></tr>
 *   <tr><td>pinned</td><td>boolean</td><td>boolean</td><td>boolean (is_pinned)</td></tr>
 * </table>
 *
 * <p>插件端的对应实现见 {@code duxiang-ext/js/eventlog.js} 的
 * {@code toWire} / {@code fromWire} —— 两边必须逐字段对齐。</p>
 *
 * <p>注意：本类**不包含** Android 独有的字段（remark / source_app /
 * original_intent / target_activity / summary / click_count）。那些字段
 * 不同步 —— 两端没有共同语义，同步过去只会制造冲突。</p>
 */
public final class LinkWire {

    /** wire 格式里的字段名（协议层规范名，两端一致）。 */
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_URL = "url";
    public static final String FIELD_TAGS = "tags";
    public static final String FIELD_TIMESTAMP = "timestamp";
    public static final String FIELD_PINNED = "pinned";

    /** 需要参与字段级 LWW 的字段集合 —— 顺序固定，便于比对。 */
    public static final String[] SYNCED_FIELDS = {
            FIELD_TITLE, FIELD_URL, FIELD_TAGS, FIELD_TIMESTAMP, FIELD_PINNED
    };

    private LinkWire() {
    }

    /**
     * Android 本地 LinkItem → wire 格式 JSON。
     *
     * @param item 本地链接
     * @return wire 格式 JSON；item 为 null 时返回 null
     */
    public static String toWireJson(LinkItem item) {
        if (item == null) return null;
        try {
            JSONObject o = new JSONObject();
            o.put(FIELD_TITLE, item.getTitle() == null ? "" : item.getTitle());
            o.put(FIELD_URL, item.getUrl() == null ? "" : item.getUrl());
            o.put(FIELD_TAGS, toWireTags(item.getTags()));
            o.put(FIELD_TIMESTAMP, item.getTimestamp());
            o.put(FIELD_PINNED, item.isPinned());
            return o.toString();
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * wire 格式 JSON → Android 本地 LinkItem。
     *
     * <p>只还原共有字段；Android 独有字段（remark 等）保持默认值。</p>
     *
     * @param json wire 格式 JSON
     * @return LinkItem；解析失败返回 null
     */
    public static LinkItem fromWireJson(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            JSONObject o = new JSONObject(json);
            LinkItem item = new LinkItem(
                    o.optString(FIELD_TITLE, ""),
                    o.optString(FIELD_URL, ""),
                    "",   // source_app — 不同步
                    "",   // original_intent — 不同步
                    ""    // target_activity — 不同步
            );
            item.setTimestamp(o.optLong(FIELD_TIMESTAMP, System.currentTimeMillis()));
            item.setPinned(o.optBoolean(FIELD_PINNED, false));
            item.setTags(fromWireTags(o.optJSONArray(FIELD_TAGS)));
            return item;
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * wire 格式 JSON → 只含共有字段的字段值表。
     *
     * <p>用于字段级 LWW 的 diff —— 两边（old / data）都是 wire 格式，
     * 字段名和类型一致，diff 才有意义。</p>
     */
    public static JSONObject wireFields(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            JSONObject o = new JSONObject(json);
            JSONObject out = new JSONObject();
            for (String f : SYNCED_FIELDS) {
                if (o.has(f)) out.put(f, o.get(f));
            }
            return out;
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * 算出两个 wire 快照之间的差异字段名集合。
     *
     * <p>字段级 LWW 的核心（PROTOCOL §5.4）。</p>
     *
     * @param old 变更前快照（wire 格式）；null 表示首次见到该实体
     * @param data 变更后快照（wire 格式）；null 表示 delete 事件
     * @return 发生变化的字段名集合；delete 事件返回空集合
     */
    public static Set<String> diffFields(String old, String data) {
        Set<String> changed = new java.util.LinkedHashSet<>();
        if (data == null) {
            return changed; // delete 事件不参与字段级合并
        }
        JSONObject a = wireFields(old);
        JSONObject b = wireFields(data);
        if (a == null) {
            // old 缺失 → 退化为实体级：全部字段都算改动
            for (String f : SYNCED_FIELDS) {
                if (b.has(f)) changed.add(f);
            }
            return changed;
        }
        for (String f : SYNCED_FIELDS) {
            if (!b.has(f)) continue;
            if (!valuesEqual(a.opt(f), b.opt(f))) {
                changed.add(f);
            }
        }
        return changed;
    }

    /**
     * 按字段级 LWW 把远端快照合并到本地实体上。
     *
     * @param local  本地实体（wire 字段值表）
     * @param remote 远端快照（wire 字段值表）
     * @param changed 改动的字段名集合；null 表示整体覆盖（实体级 LWW）
     * @return 合并后的实体（wire 字段值表）
     */
    public static JSONObject mergeByFields(JSONObject local, JSONObject remote,
                                           Set<String> changed) {
        if (remote == null) return local;
        if (changed == null) {
            return mergeAll(local, remote);
        }
        JSONObject out = local == null ? new JSONObject() : copy(local);
        for (String f : changed) {
            if (remote.has(f)) {
                try {
                    out.put(f, remote.get(f));
                } catch (JSONException ignored) {
                    // 理论上不会发生（remote 是刚解析出来的 JSONObject）
                }
            }
        }
        return out;
    }

    private static JSONObject mergeAll(JSONObject local, JSONObject remote) {
        JSONObject out = local == null ? new JSONObject() : copy(local);
        Iterator<String> keys = remote.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            try {
                out.put(k, remote.get(k));
            } catch (JSONException ignored) {
            }
        }
        return out;
    }

    private static JSONObject copy(JSONObject src) {
        JSONObject out = new JSONObject();
        Iterator<String> keys = src.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            try {
                out.put(k, src.get(k));
            } catch (JSONException ignored) {
            }
        }
        return out;
    }

    /**
     * 比较两个 JSON 字段值是否相等。
     *
     * <p>数组（tags）要逐元素比 —— {@code JSONArray.equals} 是引用比较，
     * 不能用。</p>
     */
    private static boolean valuesEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a instanceof JSONArray && b instanceof JSONArray) {
            JSONArray x = (JSONArray) a;
            JSONArray y = (JSONArray) b;
            if (x.length() != y.length()) return false;
            for (int i = 0; i < x.length(); i++) {
                if (!valuesEqual(x.opt(i), y.opt(i))) return false;
            }
            return true;
        }
        if (a instanceof JSONObject && b instanceof JSONObject) {
            return a.toString().equals(b.toString());
        }
        // 数值类型可能是 Integer / Long / Double —— 按字符串比
        return String.valueOf(a).equals(String.valueOf(b));
    }

    private static JSONArray toWireTags(List<String> tags) {
        JSONArray arr = new JSONArray();
        if (tags == null) return arr;
        for (String t : tags) {
            if (t != null && !t.trim().isEmpty()) {
                arr.put(t.trim());
            }
        }
        return arr;
    }

    private static List<String> fromWireTags(JSONArray arr) {
        List<String> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            String t = arr.optString(i, "");
            if (t != null && !t.trim().isEmpty()) {
                out.add(t.trim());
            }
        }
        return out;
    }
}
