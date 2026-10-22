package person.notfresh.readingshare.core.reader;

import java.util.Optional;

import person.notfresh.readingshare.model.OverrideAction;

/**
 * URL 路由判定（内核，纯函数，无副作用，可单测）。
 *
 * <p>把 WebViewActivity.handleUrlOverride 的"该不该拦、拦了做什么"从"怎么做"里剥出来：
 * 本类只输出 {@link Route}，真正的打开外部应用 / 弹窗由调用方的 UI 层执行。</p>
 *
 * <p>判定顺序与原实现一致：
 * http(s) → WebView 内加载；非 http(s) 先看白名单动作，未命中再看全局
 * external_link_mode（0=弹窗确认 1=直接跳转 2=静默拦截）。</p>
 */
public final class UrlRoute {

    public enum Route {
        /** WebView 自己加载（shouldOverrideUrlLoading 返回 false）。 */
        LOAD_IN_WEBVIEW,
        /** 交给外部应用（startActivity）。 */
        OPEN_EXTERNAL,
        /** 弹窗问用户。 */
        ASK_USER,
        /** 静默拦截（浏览器内核不再尝试加载 scheme）。 */
        BLOCK
    }

    private UrlRoute() {
        // utility class
    }

    /**
     * @param url              即将打开的 URL
     * @param overrideAction   命中白名单规则的动作；未命中传 empty
     * @param externalLinkMode 未命中时的全局模式：0=弹窗 1=跳转 2=拦截
     */
    public static Route decide(String url, Optional<OverrideAction> overrideAction, int externalLinkMode) {
        if (url == null || url.isEmpty()) return Route.BLOCK;
        if (isHttp(url)) return Route.LOAD_IN_WEBVIEW;
        if (overrideAction != null && overrideAction.isPresent()) {
            switch (overrideAction.get()) {
                case OPEN:
                    return Route.OPEN_EXTERNAL;
                case ASK:
                    return Route.ASK_USER;
                case BLOCK:
                default:
                    return Route.BLOCK;
            }
        }
        if (externalLinkMode == 0) return Route.ASK_USER;
        if (externalLinkMode == 1) return Route.OPEN_EXTERNAL;
        return Route.BLOCK;
    }

    public static boolean isHttp(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }
}
