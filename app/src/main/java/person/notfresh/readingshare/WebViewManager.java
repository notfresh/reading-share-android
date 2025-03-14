package person.notfresh.readingshare;

import android.webkit.WebView;
import android.view.ViewGroup;
import java.util.HashMap;
import java.util.Map;

/**
 * WebView实例管理器 - 存储和重用WebView实例
 */
public class WebViewManager {
    private static WebViewManager instance;
    private Map<String, WebView> cachedWebViews = new HashMap<>();
    
    private WebViewManager() {}
    
    public static synchronized WebViewManager getInstance() {
        if (instance == null) {
            instance = new WebViewManager();
        }
        return instance;
    }
    
    /**
     * 存储WebView实例
     */
    public void storeWebView(String url, WebView webView) {
        // 限制最大缓存数量为5个，避免内存问题
        if (cachedWebViews.size() >= 5) {
            // 如果已经有5个缓存，移除最早加入的
            String firstKey = cachedWebViews.keySet().iterator().next();
            WebView oldWebView = cachedWebViews.remove(firstKey);
            if (oldWebView != null) {
                oldWebView.destroy();
            }
        }
        
        // 注入一个脚本，清理可能导致问题的媒体会话
        webView.evaluateJavascript(
            "try {" +
            "  var mediaElements = document.querySelectorAll('audio,video');" +
            "  for(var i=0; i<mediaElements.length; i++) {" +
            "    mediaElements[i].pause();" +
            "  }" +
            "} catch(e) { console.log(e); }", null);
        
        cachedWebViews.put(url, webView);
    }
    
    /**
     * 获取已缓存的WebView
     */
    public WebView getWebView(String url) {
        return cachedWebViews.get(url);
    }
    
    /**
     * 检查URL是否有缓存
     */
    public boolean hasCache(String url) {
        return cachedWebViews.containsKey(url);
    }
    
    /**
     * 移除WebView缓存
     */
    public void removeWebView(String url) {
        WebView webView = cachedWebViews.remove(url);
        if (webView != null) {
            webView.destroy();
        }
    }
    
    /**
     * 清除所有缓存
     */
    public void clearAll() {
        for (WebView webView : cachedWebViews.values()) {
            webView.destroy();
        }
        cachedWebViews.clear();
    }
} 