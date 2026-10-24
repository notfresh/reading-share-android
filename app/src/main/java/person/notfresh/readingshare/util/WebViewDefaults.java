package person.notfresh.readingshare.util;

import android.webkit.WebSettings;
import android.webkit.WebView;

/**
 * WebView 基础配置（共享单元）。
 *
 * <p>与 WebViewActivity.setupWebView 的 settings 段保持一致（JS / 缩放 / DOM storage /
 * 混合内容 / 媒体自动播放）。Fragment 侧直接调用；Activity 侧待第二刀迁过来，
 * 届时删掉它自己那 15 行，配置只剩这一份。</p>
 */
public final class WebViewDefaults {

    private WebViewDefaults() {
        // utility class
    }

    public static void apply(WebView webView) {
        if (webView == null) return;
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setDomStorageEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);
        // 2.3.22:缓存基线改 LOAD_DEFAULT(按服务器缓存头决定,过期会回源),
        // "本地缓存最多 1 小时"的上限由 WebViewCachePolicy 在每次加载前判定。
        // 不再是 LOAD_CACHE_ELSE_NETWORK —— Chromium 把它映射成
        // net::LOAD_SKIP_CACHE_VALIDATION,过期也不校验,页面会永远停在旧版本。
        WebViewCachePolicy.apply(webView);
    }
}
