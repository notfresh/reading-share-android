package person.notfresh.readingshare;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * 折叠期 WebView 持有器.
 * 用 Application Context 创建的 FrameLayout 作为 webView 的临时 parent,
 * 让 webView 在 Activity finish 后仍能存活,音频继续.
 *
 * 严格单例:折叠期间最多持有一个 webView.
 */
public class CollapsedWebViewHolder {
    private static volatile CollapsedWebViewHolder instance;
    private FrameLayout container;

    private CollapsedWebViewHolder() {}

    public static CollapsedWebViewHolder getInstance() {
        if (instance == null) {
            synchronized (CollapsedWebViewHolder.class) {
                if (instance == null) {
                    instance = new CollapsedWebViewHolder();
                }
            }
        }
        return instance;
    }

    /**
     * 获取 holder 内部的 FrameLayout,首次调用会用 applicationContext 创建.
     */
    public ViewGroup getContainer(Context appContext) {
        if (container == null) {
            container = new FrameLayout(appContext);
        }
        return container;
    }

    public void reset() {
        container = null;
    }
}
