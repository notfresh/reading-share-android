package person.notfresh.readingshare.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 单条 WebView 跳转白名单规则。不可变 record-like。
 *
 * <p>匹配语义(2026-10-12 第三轮拍板):</p>
 * <ul>
 *   <li>{@code contextUrl} 原始 URL 子串 — 用户在手填入对话框时填入,通常是用户浏览器地址栏起初看到的那条
 *       (可以是原始短链 xhslink.cn,也可以是直接指向 xiaohongshu.com 的原始链 —— 两者都是 contextUrl 的合法值)</li>
 *   <li>{@code redirectedUrl} webview 重定向后的真页 URL —— 系统自动记录(用户不能手填)</li>
 *   <li>{@code action} 命中后动作:ASK / OPEN / BLOCK</li>
 * </ul>
 *
 * <p>app scheme(xhsdiscover://...)不参与匹配 —— 不管是什么 scheme,
 * 只要原 URL 重定向链中的任一环命中就按 action 走。</p>
 *
 * <p>匹配候选也是两条:当前 webview 的 getOriginalUrl() 与 getUrl()。
 * 重定向后这两个不同;不重定向场景这两个一样(查重不影响命中)。</p>
 *
 * <p>字段自动补全:首次命中某条规则的 contextUrl 时,如果 redirectedUrl 为空、
 * 且 getUrl != getOriginalUrl(确认发生重定向),系统会写入 redirectedUrl。
 * 下次匹配走该字段。</p>
 *
 * <p>兼容老数据:旧 JSON 只有 {@code pattern} 字段会自动映射到 {@code contextUrl}。</p>
 */
public final class WebViewOverrideRule {

    private final String contextUrl;
    private final String redirectedUrl;
    private final OverrideAction action;

    public WebViewOverrideRule(String contextUrl, OverrideAction action) {
        this(contextUrl, null, action);
    }

    public WebViewOverrideRule(String contextUrl, String redirectedUrl, OverrideAction action) {
        this.contextUrl = contextUrl;
        this.redirectedUrl = redirectedUrl;
        this.action = action;
    }

    public String getContextUrl() {
        return contextUrl;
    }

    /** 可能为 null 或空,表示尚未记录重定向后的 url。 */
    public String getRedirectedUrl() {
        return redirectedUrl;
    }

    public OverrideAction getAction() {
        return action;
    }

    /** 不可变更新 redirectedUrl — 工厂方法。 */
    public WebViewOverrideRule withRedirectedUrl(String newRedirectedUrl) {
        return new WebViewOverrideRule(contextUrl, newRedirectedUrl, action);
    }

    /** 序列化成 JSON object。 */
    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("contextUrl", contextUrl);
        if (redirectedUrl != null && !redirectedUrl.isEmpty()) {
            o.put("redirectedUrl", redirectedUrl);
        }
        o.put("action", action.name());
        return o;
    }

    /** 反序列化。兼容旧字段 pattern → contextUrl。 */
    public static WebViewOverrideRule fromJson(JSONObject o) {
        String contextUrl = o.optString("contextUrl", "");
        if (contextUrl == null || contextUrl.isEmpty()) {
            contextUrl = o.optString("pattern", "");
        }
        String redirectedUrl = o.optString("redirectedUrl", "");
        String actionStr = o.optString("action", "ASK");
        OverrideAction action;
        try {
            action = OverrideAction.valueOf(actionStr);
        } catch (IllegalArgumentException ignored) {
            action = OverrideAction.ASK;
        }
        return new WebViewOverrideRule(contextUrl, redirectedUrl, action);
    }
}
