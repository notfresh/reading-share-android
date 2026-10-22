package person.notfresh.readingshare.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import person.notfresh.readingshare.model.WebViewOverrideRule;

/**
 * WebView 跳转白名单规则的数据层(单一真相源 = SharedPreferences "settings" / override_rules_json)。
 *
 * <p>2026-10-12 拍板(第三轮):每条规则 = contextUrl(原始 URL 子串 + 用户手填) + redirectedUrl(可选,系统自动写)
 * + action(ASK/OPEN/BLOCK)。匹配走两个候选(getUrl + getOriginalUrl)查 contextUrl OR redirectedUrl,
 * 任一命中胜出 —— 自动消化"原始短链 302 重定向到真页 url 怎么配都不上"的痛点。</p>
 *
 * <p>老 2.3.17 之前的数据(只 pattern 字段)由 {@link WebViewOverrideRule#fromJson}
 * 自动映射到 contextUrl。</p>
 */
public final class WebViewOverrideRules {

    public static final String PREFS_NAME = "settings";
    public static final String KEY_RULES_JSON = "override_rules_json";

    private static final String TAG = "OverrideRules";

    private WebViewOverrideRules() {
        // utility class
    }

    public static List<WebViewOverrideRule> loadAll(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_RULES_JSON, "");
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        List<WebViewOverrideRule> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                WebViewOverrideRule r = WebViewOverrideRule.fromJson(o);
                if (r.getContextUrl() == null || r.getContextUrl().trim().isEmpty()) continue;
                out.add(r);
            }
        } catch (JSONException e) {
            Log.e(TAG, "loadAll: parse failed, returning empty. raw=" + raw, e);
            return new ArrayList<>();
        }
        return out;
    }

    /**
     * 命中检查:任一 candidate URL(contextUrl 或 redirectedUrl)被子串包含(忽略大小写)的规则胜出。
     * 大小写不敏感。
     *
     * @param ctx android context
     * @param candidates 候选 URL 列表 —— 调用方传 webView.getUrl() 与 webView.getOriginalUrl()
     */
    public static Optional<WebViewOverrideRule> firstMatch(Context ctx, List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) return Optional.empty();
        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty()) continue;
            String haystack = candidate.toLowerCase(Locale.ROOT);
            for (WebViewOverrideRule r : loadAll(ctx)) {
                if (haystack.contains(r.getContextUrl().toLowerCase(Locale.ROOT))) {
                    return Optional.of(r);
                }
                String redir = r.getRedirectedUrl();
                if (redir != null && !redir.isEmpty()
                        && haystack.contains(redir.toLowerCase(Locale.ROOT))) {
                    return Optional.of(r);
                }
            }
        }
        return Optional.empty();
    }

    /** 兼容老签名的 1-arg 重载。 */
    public static Optional<WebViewOverrideRule> firstMatch(Context ctx, String currentPageUrl) {
        List<String> cands = new ArrayList<>(2);
        if (currentPageUrl != null && !currentPageUrl.isEmpty()) cands.add(currentPageUrl);
        return firstMatch(ctx, cands);
    }

    /**
     * 自动补写规则:首次命中某条规则 contextUrl 后,如果该规则 redirectedUrl 为空,
     * 且 candidate 与 contextUrl 不同(说明发生重定向),把 candidate 写入 redirectedUrl。
     *
     * @return true 表示写入过(false = 已存在/无需写入)
     */
    public static boolean recordRedirectedUrl(Context ctx, String contextUrl, String candidate) {
        if (contextUrl == null || contextUrl.isEmpty()) return false;
        if (candidate == null || candidate.isEmpty()) return false;
        if (contextUrl.equalsIgnoreCase(candidate)) return false; // 没重定向
        List<WebViewOverrideRule> all = loadAll(ctx);
        for (int i = 0; i < all.size(); i++) {
            WebViewOverrideRule r = all.get(i);
            if (r.getContextUrl().equalsIgnoreCase(contextUrl)) {
                if (r.getRedirectedUrl() != null && !r.getRedirectedUrl().isEmpty()) {
                    return false;
                }
                all.set(i, r.withRedirectedUrl(candidate));
                saveAll(ctx, all);
                return true;
            }
        }
        return false;
    }

    public static void saveAll(Context ctx, List<WebViewOverrideRule> rules) {
        JSONArray arr = new JSONArray();
        if (rules != null) {
            for (WebViewOverrideRule r : rules) {
                if (r == null) continue;
                if (r.getContextUrl() == null || r.getContextUrl().trim().isEmpty()) continue;
                try {
                    arr.put(r.toJson());
                } catch (JSONException e) {
                    Log.e(TAG, "saveAll: skip malformed rule contextUrl=" + r.getContextUrl(), e);
                }
            }
        }
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_RULES_JSON, arr.toString()).apply();
    }

    /** 追加一条;contextUrl 为空或与已有重复(忽略大小写)抛 IllegalArgumentException。 */
    public static void add(Context ctx, WebViewOverrideRule rule) {
        if (rule == null || rule.getContextUrl() == null || rule.getContextUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("rule or contextUrl empty");
        }
        List<WebViewOverrideRule> all = loadAll(ctx);
        for (WebViewOverrideRule existing : all) {
            if (existing.getContextUrl().equalsIgnoreCase(rule.getContextUrl())) {
                throw new IllegalArgumentException("duplicate contextUrl: " + rule.getContextUrl());
            }
        }
        all.add(rule);
        saveAll(ctx, all);
    }

    public static void update(Context ctx, int index, WebViewOverrideRule rule) {
        if (rule == null || rule.getContextUrl() == null || rule.getContextUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("rule or contextUrl empty");
        }
        List<WebViewOverrideRule> all = loadAll(ctx);
        if (index < 0 || index >= all.size()) {
            throw new IndexOutOfBoundsException("index=" + index + " size=" + all.size());
        }
        all.set(index, rule);
        saveAll(ctx, all);
    }

    public static void remove(Context ctx, int index) {
        List<WebViewOverrideRule> all = loadAll(ctx);
        if (index < 0 || index >= all.size()) {
            throw new IndexOutOfBoundsException("index=" + index + " size=" + all.size());
        }
        all.remove(index);
        saveAll(ctx, all);
    }

    public static List<WebViewOverrideRule> loadAllUnmodifiable(Context ctx) {
        return Collections.unmodifiableList(loadAll(ctx));
    }
}
