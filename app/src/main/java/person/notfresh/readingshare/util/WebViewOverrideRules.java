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
import java.util.Optional;

import person.notfresh.readingshare.model.WebViewOverrideRule;

/**
 * WebView 跳转白名单规则的数据层(单一真相源 = SharedPreferences "settings" / override_rules_json)。
 *
 * <p>对外 API 语义:</p>
 * <ul>
 *   <li>{@link #loadAll(Context)} - 从 SP 读 JSON array,空/坏数据 → 返回空列表</li>
 *   <li>{@link #firstMatch(Context, String)} - 顺序遍历,首个 pattern 子串匹配即返回 Optional</li>
 *   <li>{@link #saveAll(Context, List)} - 全量覆盖写入</li>
 *   <li>{@link #add}/{@link #update}/{@link #remove} - 增量操作(均走 saveAll)</li>
 * </ul>
 *
 * <p>设计选择:</p>
 * <ul>
 *   <li>不缓存:每次调用现读 SP,增删立刻生效</li>
 *   <li>JSON 而非 DB:规则数 0~20 量级,JSON 序列号已足,不强加 Room</li>
 *   <li>复入现有 "settings" SP,不开新 SP</li>
 * </ul>
 */
public final class WebViewOverrideRules {

    /** SharedPreferences 文件名(与 SettingFragment.getSharedPreferences("settings", ...) 一致)。 */
    public static final String PREFS_NAME = "settings";
    /** 规则列表在 SP 里的 key。 */
    public static final String KEY_RULES_JSON = "override_rules_json";

    private static final String TAG = "OverrideRules";

    private WebViewOverrideRules() {
        // utility class
    }

    /**
     * 读出全部规则。空 / 解析失败 → 返回空列表(永不抛异常)。
     */
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
                // 空 pattern 的脏数据跳过
                if (r.getPattern() == null || r.getPattern().trim().isEmpty()) continue;
                out.add(r);
            }
        } catch (JSONException e) {
            Log.e(TAG, "loadAll: parse failed, returning empty. raw=" + raw, e);
            return new ArrayList<>();
        }
        return out;
    }

    /**
     * 命中检查:首个 pattern 包含(url 子串包含 pattern)的规则胜出。
     * 大小写不敏感。
     */
    public static Optional<WebViewOverrideRule> firstMatch(Context ctx, String url) {
        if (url == null || url.isEmpty()) return Optional.empty();
        String haystack = url.toLowerCase(java.util.Locale.ROOT);
        for (WebViewOverrideRule r : loadAll(ctx)) {
            String needle = r.getPattern().toLowerCase(java.util.Locale.ROOT);
            if (haystack.contains(needle)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    /**
     * 全量覆盖写入。如果传入空列表则写出 "[]"(不清 key,保持 SP key 存在以便列表迭代稳定)。
     */
    public static void saveAll(Context ctx, List<WebViewOverrideRule> rules) {
        JSONArray arr = new JSONArray();
        if (rules != null) {
            for (WebViewOverrideRule r : rules) {
                if (r == null) continue;
                if (r.getPattern() == null || r.getPattern().trim().isEmpty()) continue;
                try {
                    arr.put(r.toJson());
                } catch (JSONException e) {
                    Log.e(TAG, "saveAll: skip malformed rule pattern=" + r.getPattern(), e);
                }
            }
        }
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_RULES_JSON, arr.toString()).apply();
    }

    /** 追加一条;pattern 为空或与已有重复(忽略大小写)直接抛 IllegalArgumentException 让调用方决策。 */
    public static void add(Context ctx, WebViewOverrideRule rule) {
        if (rule == null || rule.getPattern() == null || rule.getPattern().trim().isEmpty()) {
            throw new IllegalArgumentException("rule or pattern empty");
        }
        List<WebViewOverrideRule> all = loadAll(ctx);
        for (WebViewOverrideRule existing : all) {
            if (existing.getPattern().equalsIgnoreCase(rule.getPattern())) {
                throw new IllegalArgumentException("duplicate pattern: " + rule.getPattern());
            }
        }
        all.add(rule);
        saveAll(ctx, all);
    }

    public static void update(Context ctx, int index, WebViewOverrideRule rule) {
        if (rule == null || rule.getPattern() == null || rule.getPattern().trim().isEmpty()) {
            throw new IllegalArgumentException("rule or pattern empty");
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

    /** 测试/调试用:返回不可修改快照。 */
    public static List<WebViewOverrideRule> loadAllUnmodifiable(Context ctx) {
        return Collections.unmodifiableList(loadAll(ctx));
    }
}
