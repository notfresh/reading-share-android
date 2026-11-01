package person.notfresh.readingshare.util;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 「Chat with AI」上下文组装（2026-11-01,docs/261101-feature-Guess you like.md 同期需求）。
 *
 * <p>单一来源：WebView 菜单和其它入口都从这里拿预填文本,AgentFragment 只消费。</p>
 *
 * <p>组装规则（用户拍板）：
 * <ul>
 *   <li>标题、URL 基本必有;正文尽力提取（CrawlUtil）,拿不到就只有标题+URL,不报错;</li>
 *   <li>正文截断 {@value #BODY_MAX_CHARS} 字 —— 起点够 LLM 讨论,又不撑爆上下文;</li>
 *   <li>组装结果<b>填进输入框不自动发送</b> —— 用户补自己的问题再点发送。</li>
 * </ul></p>
 */
public final class ChatContextBuilder {

    /** 正文截断上限（字符）。 */
    public static final int BODY_MAX_CHARS = 2000;

    private ChatContextBuilder() {
        // utility class
    }

    /**
     * 组装预填文本。
     *
     * @param title 页面标题（可 null/空）
     * @param url   页面 URL（可 null/空）
     * @param body  已提取的正文（可 null/空 —— 多媒体/加载失败场景）
     * @return 形如「【正在讨论】…【链接】…【正文】…」的预填文本;三个都空返回空串
     */
    public static String build(String title, String url, String body) {
        boolean hasTitle = title != null && !title.trim().isEmpty();
        boolean hasUrl = url != null && !url.trim().isEmpty();
        boolean hasBody = body != null && !body.trim().isEmpty();
        if (!hasTitle && !hasUrl && !hasBody) return "";

        StringBuilder sb = new StringBuilder();
        if (hasTitle) sb.append("【正在讨论】").append(title.trim()).append('\n');
        if (hasUrl) sb.append("【链接】").append(url.trim()).append('\n');
        if (hasBody) {
            String trimmed = body.trim();
            if (trimmed.length() > BODY_MAX_CHARS) {
                trimmed = trimmed.substring(0, BODY_MAX_CHARS) + "…(正文已截断)";
            }
            sb.append("【正文】\n").append(trimmed);
        }
        sb.append('\n');
        return sb.toString();
    }

    /**
     * 组装结果的 JSON 形态 —— 跨 Fragment 传参用（AgentFragment args）。
     * 字段：title / url / body。
     */
    public static String toJson(String title, String url, String body) {
        JSONObject o = new JSONObject();
        try {
            o.put("title", title == null ? "" : title);
            o.put("url", url == null ? "" : url);
            o.put("body", body == null ? "" : body);
        } catch (JSONException e) {
            // JSONException 只在 key 为 null 时抛,key 是常量,不会走到
        }
        return o.toString();
    }

    /** 从 JSON 还原三要素;坏数据返回 null（调用方降级为无上下文）。 */
    public static String[] fromJson(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            JSONObject o = new JSONObject(json);
            return new String[]{
                    o.optString("title", ""),
                    o.optString("url", ""),
                    o.optString("body", "")
            };
        } catch (JSONException e) {
            return null;
        }
    }
}
