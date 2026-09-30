package person.notfresh.readingshare.external;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.util.HashSet;
import java.util.Set;

/**
 * 外部链接黑名单(全局 host 维度,持久化到 SharedPreferences)。
 *
 * <p>用户在 WebViewActivity 弹窗中点「拒绝并禁止弹窗」后,
 * 该 URL 的 host(抽不到则用 URL 本身)加入黑名单。
 * 后续同一 host 的外部链接一律静默拦截,不再弹窗。</p>
 *
 * <p>调用方只需关心两个 API:</p>
 * <ul>
 *   <li>{@link #contains(String)} — 该 URL 是否已被屏蔽</li>
 *   <li>{@link #block(String)} — 把该 URL 的 host 加入黑名单</li>
 * </ul>
 *
 * <p>存储格式:SharedPreferences Set&lt;String&gt;,key 名见 {@link #PREFS_KEY}。</p>
 */
public final class ExternalLinkBlocklist {

    private static final String PREFS_NAME = "settings";
    private static final String PREFS_KEY = "blocked_external_hosts";

    private ExternalLinkBlocklist() {
        // utility class
    }

    /**
     * 抽出用于黑名单匹配的 key:优先 host,抽不到(自定义 scheme 等)则用整个 URL。
     * 公开以便 UI 层在 Toast/日志里展示用户看到的"屏蔽对象"。
     */
    public static String keyOf(String url) {
        if (url == null) return null;
        String host = Uri.parse(url).getHost();
        return host != null ? host : url;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static Set<String> load(Context ctx) {
        return new HashSet<>(prefs(ctx).getStringSet(PREFS_KEY, new HashSet<>()));
    }

    /** 该 URL 是否在黑名单中 */
    public static boolean contains(Context ctx, String url) {
        String key = keyOf(url);
        if (key == null) return false;
        Set<String> set = prefs(ctx).getStringSet(PREFS_KEY, null);
        return set != null && set.contains(key);
    }

    /** 把该 URL 的 host 加入黑名单并持久化 */
    public static void block(Context ctx, String url) {
        String key = keyOf(url);
        if (key == null) return;
        Set<String> set = load(ctx);
        set.add(key);
        prefs(ctx).edit().putStringSet(PREFS_KEY, set).apply();
    }
}
