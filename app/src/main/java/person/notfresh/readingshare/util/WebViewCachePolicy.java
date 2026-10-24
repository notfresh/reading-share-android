package person.notfresh.readingshare.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * WebView 缓存策略的<b>唯一来源</b>（2.3.22）。
 *
 * <p>两个能力共用这一份，不各自散落：
 * <ul>
 *   <li><b>本地缓存最多 1 小时</b> —— {@link #prepareLoad} 在每次加载前判断这个 URL 的
 *       本地副本是否已超过 {@link #MAX_CACHE_AGE_MS}；超了就用 {@code LOAD_NO_CACHE} 绕过
 *       缓存重取，没超就走 {@code LOAD_DEFAULT}（缓存有效就用，否则回源）。</li>
 *   <li><b>立即强制刷新</b> —— {@link #forceReload} 用 {@code LOAD_NO_CACHE} 重载当前页，
 *       完全不走本地缓存。</li>
 * </ul>
 *
 * <p><b>为什么不用 LOAD_CACHE_ELSE_NETWORK</b>：Chromium 把
 * {@code LOAD_CACHE_ELSE_NETWORK} 映射成 {@code net::LOAD_SKIP_CACHE_VALIDATION}
 * （见 chromium {@code android_webview/browser/network_service/net_helpers.cc} 的
 * {@code GetCacheModeForClient}）——<b>过期也不校验，直接吃本地副本</b>。那种模式下
 * "缓存最多 1 小时"根本无从谈起（页面会永远停在旧版本），所以基线改成 {@code LOAD_DEFAULT}，
 * 由本类在应用层补上 1 小时上限。</p>
 *
 * <p><b>缓存模式是 WebView 级别的持久状态</b>，不是"这一次请求"的属性 —— 一旦设成
 * {@code LOAD_NO_CACHE}，页面内跳转（走 {@code shouldOverrideUrlLoading} 直接交给内核）
 * 也会继续绕过缓存。所以本类提供 {@link #restoreBaseline}，由宿主在
 * {@code onPageFinished} 里把模式还原，避免"绕过缓存"泄漏到后续导航。</p>
 *
 * <p><b>记账只发生在绕过缓存时</b>（{@link #prepareLoad} 判定超期 + {@link #forceReload}），
 * 不在每次加载完成时记 —— 否则"从缓存命中"也会刷新时间戳，一小时窗口会被无限滑动，
 * 频繁打开的页面永远命中旧副本。</p>
 *
 * <p>存储：SharedPreferences 里一个 JSON 数组，元素 [url, 毫秒时间戳]，按写入顺序保留
 * 最近 {@link #MAX_TRACKED} 条（FIFO）。不新增数据库、不新增权限。</p>
 */
public final class WebViewCachePolicy {

    private static final String TAG = "WebViewCachePolicy";

    /** 本地缓存的最长有效期：1 小时。超过就绕过缓存重新取。 */
    public static final long MAX_CACHE_AGE_MS = 60L * 60L * 1000L;

    /** 「该 URL 最近一次从网络取到内容」最多记多少条，超出丢最旧的。 */
    private static final int MAX_TRACKED = 200;

    private static final String PREFS = "webview_cache_policy";
    private static final String KEY_FETCHED_AT = "fetched_at";

    private WebViewCachePolicy() {
        // utility class
    }

    // ---- 装载到 WebView ----

    /**
     * 把基线缓存模式装到 WebView 上。
     *
     * <p>用 {@code LOAD_DEFAULT} 而不是 {@code LOAD_CACHE_ELSE_NETWORK}：前者按服务器
     * 缓存头决定用不用本地副本、过期会回源；后者过期也不校验（见类注释）。</p>
     */
    public static void apply(WebView webView) {
        if (webView == null) return;
        webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
    }

    /**
     * 把缓存模式还原成基线（{@code LOAD_DEFAULT}）。
     *
     * <p>宿主在 {@code onPageFinished} 里调一次：本次「绕过缓存」已经生效，
     * 后续导航不该继续绕过。</p>
     */
    public static void restoreBaseline(WebView webView) {
        if (webView == null) return;
        webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
    }

    /**
     * 加载前决定这一次的缓存模式：超过 {@link #MAX_CACHE_AGE_MS} 就绕过缓存重取。
     *
     * <p>调用方在 {@code loadUrl} <b>之前</b>调一次即可，不要在加载后再调
     * （缓存模式是发请求那一刻生效的）。</p>
     *
     * <p>超期时顺手记账：本次要绕过缓存 = 内容会从网络来 = 重新起算 1 小时。
     * 未超期（缓存命中）<b>不记账</b> —— 本地副本还是原来那份，账不该被刷新，
     * 否则一小时窗口会被滑动，频繁打开的页面永远命中旧副本。</p>
     *
     * @param context 用于读写时间戳记录，可传 Activity/Application 上下文
     * @param url     本次要加载的 URL
     */
    public static void prepareLoad(WebView webView, Context context, String url) {
        if (webView == null) return;
        boolean stale = isStale(context, url);
        webView.getSettings().setCacheMode(
                stale ? WebSettings.LOAD_NO_CACHE : WebSettings.LOAD_DEFAULT);
        if (stale) {
            // 本次要走网络 = 内容来自网络 = 重新起算 1 小时窗口。
            // 缓存命中时**不**记账：本地副本没变，账不该被刷新。
            markFetched(context, url);
        }
    }

    /**
     * 强制刷新当前页：完全绕过本地缓存重载。
     *
     * <p>缓存模式会被宿主在 {@code onPageFinished} 里用 {@link #restoreBaseline} 还原；
     * 下一次正常加载也会由 {@link #prepareLoad} 重新决定。</p>
     */
    public static void forceReload(WebView webView, Context context, String url) {
        if (webView == null) return;
        webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        markFetched(context, url);
        webView.reload();
    }

    // ---- 1 小时上限的判定 ----

    /** 该 URL 的本地副本是否已超过 1 小时上限（没有记录时按超期处理，走一次网络）。 */
    public static boolean isStale(Context context, String url) {
        if (context == null || url == null || url.isEmpty()) return false;
        long fetchedAt = readFetchedAt(context, url);
        if (fetchedAt <= 0L) return true;
        return System.currentTimeMillis() - fetchedAt > MAX_CACHE_AGE_MS;
    }

    /** 记下「该 URL 刚刚从网络取到内容」。 */
    public static void markFetched(Context context, String url) {
        if (context == null || url == null || url.isEmpty()) return;
        try {
            List<String[]> entries = readEntries(context);
            removeUrl(entries, url);
            entries.add(new String[]{url, String.valueOf(System.currentTimeMillis())});
            while (entries.size() > MAX_TRACKED) {
                entries.remove(0);
            }
            writeEntries(context, entries);
        } catch (Exception e) {
            Log.w(TAG, "markFetched failed url=" + url, e);
        }
    }

    // ---- 存储 ----

    private static long readFetchedAt(Context context, String url) {
        try {
            for (String[] entry : readEntries(context)) {
                if (url.equals(entry[0])) {
                    return Long.parseLong(entry[1]);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "readFetchedAt failed url=" + url, e);
        }
        return 0L;
    }

    private static List<String[]> readEntries(Context context) {
        List<String[]> entries = new ArrayList<>();
        String raw = prefs(context).getString(KEY_FETCHED_AT, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) continue;
                String url = obj.optString("url", "");
                long at = obj.optLong("at", 0L);
                if (!url.isEmpty() && at > 0L) {
                    entries.add(new String[]{url, String.valueOf(at)});
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "readEntries failed", e);
        }
        return entries;
    }

    private static void writeEntries(Context context, List<String[]> entries) {
        try {
            JSONArray array = new JSONArray();
            for (String[] entry : entries) {
                JSONObject obj = new JSONObject();
                obj.put("url", entry[0]);
                obj.put("at", Long.parseLong(entry[1]));
                array.put(obj);
            }
            prefs(context).edit().putString(KEY_FETCHED_AT, array.toString()).apply();
        } catch (Exception e) {
            Log.w(TAG, "writeEntries failed", e);
        }
    }

    private static void removeUrl(List<String[]> entries, String url) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (url.equals(entries.get(i)[0])) {
                entries.remove(i);
            }
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
