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
        // 2.3.20:同一个 URL 再打开时优先用本地已缓存的网页资源(图片/JS/CSS),
        // 无本地缓存才走网络。不做"寄存 WebView 实例"那套(已删),
        // 想要更快就该让站点给出合理缓存头,而不是在客户端留 Activity。
        settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
    }
}
