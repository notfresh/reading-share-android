package person.notfresh.readingshare.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 单条 WebView 跳转白名单规则。不可变 record-like。
 * <p>pattern = 子串匹配(contains),action = 命中后跳转策略。
 * 序列化由 util.WebViewOverrideRules 统一负责 JSON 编解码,本类只表达数据形状。</p>
 */
public final class WebViewOverrideRule {

    private final String pattern;
    private final OverrideAction action;

    public WebViewOverrideRule(String pattern, OverrideAction action) {
        this.pattern = pattern;
        this.action = action;
    }

    public String getPattern() {
        return pattern;
    }

    public OverrideAction getAction() {
        return action;
    }

    /** 序列化成 JSON object(供外部数组包装)。key 名固定。 */
    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("pattern", pattern);
        o.put("action", action.name());
        return o;
    }

    /** 从 JSON object 反序列化。action 字符串无法识别时降级为 {@link OverrideAction#ASK}。 */
    public static WebViewOverrideRule fromJson(JSONObject o) {
        String pattern = o.optString("pattern", "");
        String actionStr = o.optString("action", "ASK");
        OverrideAction action;
        try {
            action = OverrideAction.valueOf(actionStr);
        } catch (IllegalArgumentException ignored) {
            action = OverrideAction.ASK;
        }
        return new WebViewOverrideRule(pattern, action);
    }
}
