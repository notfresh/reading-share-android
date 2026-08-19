package person.notfresh.readingshare;

import static person.notfresh.readingshare.WebViewManager.*;

import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.content.SharedPreferences;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.PowerManager;
import android.content.Context;
import android.os.Build;
import android.Manifest;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.pm.PackageManager;
import android.webkit.PermissionRequest;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.widget.EditText;
import android.widget.TextView;
import android.widget.LinearLayout;
import com.google.android.flexbox.FlexboxLayout;

import person.notfresh.readingshare.db.DbConnection;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.util.CrawlUtil;
import person.notfresh.readingshare.util.RecentTagsManager;
import person.notfresh.readingshare.util.ShareUtil;

public class WebViewActivity extends AppCompatActivity {
    private WebView webView;
    private Toolbar toolbar;
    private String currentUrl;
    private boolean audioPlaying = false;
    private boolean isAppInForeground = true;
    private MediaSessionCompat mediaSession;
    private PowerManager.WakeLock wakeLock;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private boolean preserveCache = false;
    private String pageTitleCache = "";
    private boolean isExternalOpen = false; // 是否从外部打开
    private long[] contextIds;
    private int currentIndex;
    private boolean isNavigating = false;
    private FrameLayout navigationControls;
    private Button buttonPrevious;
    private Button buttonNext;
    private Handler controlsHandler = new Handler(Looper.getMainLooper());
    private Runnable hideControlsRunnable;
    private static final int CONTROLS_AUTO_HIDE_MS = 3000;
    private boolean navigationControlsManuallyHidden = false; // 用户手动隐藏状态

    private static final String PREFS_NAME = "settings";
    private static final String KEY_NAV_CONTROLS_HIDDEN = "nav_controls_hidden";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);

        parseNavigationContext();
        setupNavigationControls();
        initControlsIfNeeded();

        // 初始化 Toolbar
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // 检查是否从外部打开（通过分享、其他应用或桌面快捷方式）
        Intent intent = getIntent();
        String action = intent != null ? intent.getAction() : null;
        boolean fromShortcut = intent != null && intent.getBooleanExtra("from_shortcut", false);
        isExternalOpen = Intent.ACTION_SEND.equals(action) || Intent.ACTION_VIEW.equals(action) || fromShortcut;
        
        // 设置返回按钮点击事件
        toolbar.setNavigationOnClickListener(v -> {
            if (isExternalOpen) {
                // 从外部打开，返回应用主界面并导航到首页
                Intent mainIntent = new Intent(this, MainActivity.class);
                mainIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                mainIntent.putExtra("navigate_to", "home");
                startActivity(mainIntent);
                finish();
            } else {
                // 从应用内部打开，正常返回
                finish();
            }
        });

        // 获取传入的URL
        currentUrl = getIntent().getStringExtra("url");
        if (currentUrl == null || currentUrl.isEmpty()) {
            // 尝试从 Intent data 中获取 URL（可能是从外部分享）
            Uri data = intent != null ? intent.getData() : null;
            if (data != null) {
                currentUrl = data.toString();
            }
        }
        
        if (currentUrl == null || currentUrl.isEmpty()) {
            Toast.makeText(this, "无效的URL", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 检查是否有缓存的WebView实例
        WebView cachedWebView = getInstance().getWebView(currentUrl);
        ViewGroup webViewContainer = findViewById(R.id.webview_container);
        
        if (cachedWebView != null) {
            // 使用缓存的WebView
            webView = cachedWebView;
            if (webView.getParent() != null) {
                ((ViewGroup) webView.getParent()).removeView(webView);
            }
            webViewContainer.addView(webView, 0);
            attachControlRevealTouchListener();
            Toast.makeText(this, "已恢复存档页面", Toast.LENGTH_SHORT).show();
        } else {
            // 创建新的WebView
            webView = findViewById(R.id.webview);
            setupWebView();
            webView.loadUrl(currentUrl);
        }

        initAudio();

        // Android 13+ 需要运行时申请通知权限，以便前台服务通知显示在通知栏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 0);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.webview_menu, menu);

        // 普通模式不显示"显示/隐藏导航控件"菜单项
        if (!isSmoothReadingMode()) {
            MenuItem toggleItem = menu.findItem(R.id.action_toggle_navigation);
            if (toggleItem != null) {
                toggleItem.setVisible(false);
            }
        }

        // 添加调试代码
        MenuItem item = menu.findItem(R.id.action_open_browser);
        if (item == null) {
            Log.e("WebViewActivityMenu", "菜单项加载失败");
        }
        return true;
    }

    @Override
    public void finish() {
        if (preserveCache) {
            // 如果需要保留缓存，只处理WebView部分
            if (webView != null) {
                webView.stopLoading();
                // 不清除历史和缓存，但停止WebView
                webView.destroy();
            }
            // 不在这里释放mediaSession和wakeLock
        } else {
            // 不需要保留缓存时，检查WebViewManager中是否有当前URL的缓存
            if (currentUrl != null && getInstance().hasCache(currentUrl)) {
                // 清除WebViewManager中的缓存
                Log.d("WebViewActivity", "清除URL缓存: " + currentUrl);
                getInstance().removeWebView(currentUrl);
            }
        }
        super.finish();  // 调用原始的finish方法
    }

    @Override
    public void onBackPressed() {
        // 如果 WebView 可以后退，则后退
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        if (!preserveCache) {  // 只有在不保留缓存时才清除WebView缓存
            // 清理 WebView
            if (webView != null) {
                webView.stopLoading();
                webView.clearHistory();
                webView.clearCache(true);
                webView.destroy();
            }
        }
        if(preserveCache){  // 只生效一次,反转回来
           preserveCache = false;
        }

        // 无论是否保留缓存，都在onDestroy中释放这些资源
        stopService(new Intent(this, WebViewBackgroundService.class));
        WebViewBackgroundService.setMediaCallback(null);
        if (audioManager != null && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        }
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        // 使用setWillNotDraw(false)确保WebView即使在后台也能继续渲染
        if (webView != null) {
            webView.setWillNotDraw(false);
        }
        super.onStop();
    }

    private void handleNavigationIntent(Intent intent) {
        String newUrl = intent.getStringExtra("url");
        if (newUrl != null && !newUrl.equals(currentUrl)) {
            currentUrl = newUrl;
            webView.loadUrl(currentUrl);
        } else if (contextIds != null && currentIndex >= 0 && currentIndex < contextIds.length) {
            loadByContextIndex(currentIndex);
        } else {
            Log.w("WebViewActivity", "Invalid navigation intent or context.");
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        parseNavigationContext();
        initControlsIfNeeded();
        handleNavigationIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isAppInForeground = true;
        initControlsIfNeeded();
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
        // 回到前台，前台服务不再需要（进程已安全）
        stopService(new Intent(this, WebViewBackgroundService.class));
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_refresh) {
            if (webView != null) webView.reload();
            return true;
        } else if (item.getItemId() == R.id.action_open_browser) {
            // 添加日志打印
            String menuTitle = item.getTitle().toString();
            Log.d("WebViewActivityMenu", "点击了菜单项: " + menuTitle);
            
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(webView.getUrl()));
                startActivity(intent);
                return true;
            } catch (Exception e) {
                Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show();
                return true;
            }
        } else if (item.getItemId() == R.id.action_force_back) {
            // 强制返回，直接关闭当前 WebView
            Log.d("WebViewActivityMenu", "点击了强制返回");
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_archive_back) {
            // 存档返回，保存整个WebView实例
            Log.d("WebViewActivityMenu", "点击了存档返回");
            Toast.makeText(this, "页面已存档", Toast.LENGTH_SHORT).show();

            // 释放音频资源
            if (audioManager != null && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            }
            stopService(new Intent(this, WebViewBackgroundService.class));
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
            if (mediaSession != null) {
                mediaSession.setActive(false);
                mediaSession.release();
                mediaSession = null;
            }

            // 从布局中移除WebView以保持其状态
            if (webView != null && webView.getParent() != null) {
                ((ViewGroup) webView.getParent()).removeView(webView);
                // 存储WebView实例
                getInstance().storeWebView(currentUrl, webView);
                // 设置webView为null以防止在onDestroy中被销毁
                webView = null;
            }
            preserveCache = true;  // 保留其他标记
            finish();
            return true;
        } else if (item.getItemId() == R.id.webview_share) {
            Log.d("WebViewActivityMenu", "点击了分享");
            String title = (pageTitleCache != null && !pageTitleCache.isEmpty()) ? pageTitleCache : (webView != null ? webView.getTitle() : "");
            String url = webView != null ? webView.getUrl() : "";
            Log.d("ShareUtil", "WebViewActivityMenu share title=" + title + ", url=" + url);
            ShareUtil.shareText(this, title, null, url);
            return true;
        } else if (item.getItemId() == R.id.action_extract_content) {
            Log.d("WebViewActivityMenu", "click extract");
            String url = webView != null ? webView.getUrl() : "";
            if(url.equals("")){
                Log.d("WebViewActivityMenu", "null url");
                Toast.makeText(this, "Url为空", Toast.LENGTH_SHORT).show();
                return true;
            }
            
            // 使用后台线程执行网络请求，避免NetworkOnMainThreadException
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Handler handler = new Handler(Looper.getMainLooper());
            
            executor.execute(() -> {
                try {
                    Log.d("WebViewActivityMenu", "@1.01");
                    String content = CrawlUtil.getArticleByUrl(url);
                    Log.d("WebViewActivityMenu", "@2");
                    // 切换回主线程更新UI
                    handler.post(() -> {
                        try {
                            // 将提取的内容写入剪贴板
                            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText("extracted_content", content);
                            clipboard.setPrimaryClip(clip);
                            Log.d("WebViewActivityMenu", "@3");
                            Toast.makeText(this, "提取Web内容完成，已经写入剪切板", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(this, "提取失败", Toast.LENGTH_SHORT).show();
                            Log.d("WebViewActivityMenu", e.toString());
                        }
                    });
                } catch (Exception e) {
                    // 切换回主线程显示错误信息
                    handler.post(() -> {
                        Toast.makeText(this, "提取失败", Toast.LENGTH_SHORT).show();
                        Log.d("WebViewActivityMenu", e.toString());
                    });
                }
            });
            
            return true;
        } else if (item.getItemId() == R.id.action_toggle_navigation) {
            toggleNavigationControls();
            return true;
        } else if (item.getItemId() == R.id.action_toggle_external_block) {
            toggleExternalBlockMode();
            return true;
        } else if (item.getItemId() == R.id.action_add_tag) {
            showAddTagDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    private void setupWebView() {
        attachControlRevealTouchListener();

        // 启用 JavaScript
        webView.getSettings().setJavaScriptEnabled(true);

        // 设置缩放控制
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);

        // 启用 DOM storage
        webView.getSettings().setDomStorageEnabled(true);

        // 允许混合内容（HTTP和HTTPS）
        webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // 允许自动播放媒体
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                super.onShowCustomView(view, callback);
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }
        });

        // 添加JS接口，用于网页通知Java层媒体播放状态
        webView.addJavascriptInterface(new MediaInterfaceObject(), "AndroidMediaInterface");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Log.d("WVUrlTrace", "shouldOverrideUrlLoading(String) url=" + url);
                return handleUrlOverride(view, url, true);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request != null && request.getUrl() != null
                        ? request.getUrl().toString()
                        : null;
                boolean isMainFrame = request == null || request.isForMainFrame();
                Log.d("WVUrlTrace", "shouldOverrideUrlLoading(Request) url=" + url
                        + ", isMainFrame=" + isMainFrame);
                return handleUrlOverride(view, url, isMainFrame);
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                try {
                    String t = view.getTitle();
                    if (t != null && !t.trim().isEmpty()) {
                        pageTitleCache = t.trim();
                    }
                    view.evaluateJavascript("document.title", value -> {
                        try {
                            if (value != null && value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                                String jsTitle = value.substring(1, value.length() - 1);
                                if (!jsTitle.trim().isEmpty()) {
                                    pageTitleCache = jsTitle.trim();
                                }
                            }
                        } catch (Exception ignore) {

                        }
                    });

                    // 对微信公众号页面做更稳健的标题提取（不依赖站点特判，通用选择器）
                    String wechatTitleScript =
                            "(function(){" +
                            "  function txt(x){var y=(x||''); return y.replace(/\\s+/g,' ').trim();}" +
                            "  var el=document.getElementById('activity-name')||document.querySelector('h1.rich_media_title, h2.rich_media_title');" +
                            "  var t=el?txt(el.innerText||el.textContent):'';" +
                            "  if(!t){var metas=document.getElementsByTagName('meta'); for(var i=0;i<metas.length;i++){ var p=metas[i].getAttribute('property'); if(p==='og:title'){ t=txt(metas[i].getAttribute('content')); break; } }}" +
                            "  if(!t){t=txt(document.title);} return t;" +
                            "})();";
                    view.evaluateJavascript(wechatTitleScript, value -> {
                        try {
                            if (value != null && value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                                String jsTitle = value.substring(1, value.length() - 1);
                                if (!jsTitle.trim().isEmpty()) {
                                    pageTitleCache = jsTitle.trim();
                                }
                            }
                        } catch (Exception ignore) {}
                    });

                    // 再延迟一次尝试，处理晚加载的标题
                    view.postDelayed(() -> view.evaluateJavascript(wechatTitleScript, value2 -> {
                        try {
                            if (value2 != null && value2.length() >= 2 && value2.startsWith("\"") && value2.endsWith("\"")) {
                                String jsTitle2 = value2.substring(1, value2.length() - 1);
                                if (!jsTitle2.trim().isEmpty()) {
                                    pageTitleCache = jsTitle2.trim();
                                }
                            }
                        } catch (Exception ignore) {

                        }
                    }), 500);
                } catch (Exception ignore) {

                }
                
                // 注入媒体播放状态监听，用于后台音频检测
                view.evaluateJavascript(
                    "(function(){" +
                    "if(window.__mediaListenerAttached)return;" +
                    "window.__mediaListenerAttached=true;" +
                    // 用 Set 保存正在播放的元素引用
                    "window.__activeMedia=new Set();" +
                    "function notify(p){if(window.AndroidMediaInterface)window.AndroidMediaInterface.setMediaPlaying(p);}" +
                    "function attach(el){" +
                    "if(el.__listenersAttached)return;el.__listenersAttached=true;" +
                    "el.addEventListener('play',function(){console.log('__media play event');window.__activeMedia.add(el);notify(true);});" +
                    "el.addEventListener('pause',function(){console.log('__media pause event');window.__activeMedia.delete(el);if(window.__activeMedia.size===0)notify(false);});" +
                    "el.addEventListener('ended',function(){console.log('__media ended event');window.__activeMedia.delete(el);if(window.__activeMedia.size===0)notify(false);})" +
                    "}" +
                    // 全局控制函数：直接操作保存的元素引用
                    "window.__pauseMedia=function(){console.log('__pauseMedia called, active='+window.__activeMedia.size);" +
                    "window.__activeMedia.forEach(function(e){try{e.pause();}catch(x){}});};" +
                    "window.__playMedia=function(){console.log('__playMedia called, active='+window.__activeMedia.size);" +
                    "window.__activeMedia.forEach(function(e){try{e.play().catch(function(){});}catch(x){}});};" +
                    "window.__stopMedia=function(){console.log('__stopMedia called');" +
                    "window.__activeMedia.forEach(function(e){try{e.pause();e.currentTime=0;}catch(x){}});window.__activeMedia.clear();};" +
                    // 附加到已有元素
                    "var existing=[].slice.call(document.getElementsByTagName('audio'))" +
                    ".concat([].slice.call(document.getElementsByTagName('video')));" +
                    "existing.forEach(attach);" +
                    // 立即检测当前是否已有音频在播放（监听前已开始的情况）
                    "existing.forEach(function(el){if(!el.paused&&!el.ended){window.__activeMedia.add(el);}});" +
                    "if(window.__activeMedia.size>0)notify(true);" +
                    // MutationObserver 监听动态添加的元素
                    "var obs=new MutationObserver(function(ms){ms.forEach(function(m){" +
                    "m.addedNodes.forEach(function(n){" +
                    "if(n.tagName==='AUDIO'||n.tagName==='VIDEO'){attach(n);}" +
                    "else if(n.querySelectorAll){[].slice.call(n.querySelectorAll('audio,video')).forEach(attach);}" +
                    "});});});" +
                    "obs.observe(document.body||document.documentElement,{childList:true,subtree:true});" +
                    "})()", null);
            }
        });
    }

    private boolean handleUrlOverride(WebView view, String url, boolean isMainFrame) {
        if (url == null ) {
            Log.d("WVUrlTrace", "handleUrlOverride early-return: url=" + url
                    + ", isMainFrame=" + isMainFrame);
            return false;
        }

        // http(s) 链接直接在 WebView 内加载
        if (url.startsWith("http://") || url.startsWith("https://")) {
            Log.d("WVUrlTrace", "handleUrlOverride http(s) → load in WebView: " + url);
            return false;
        }

        // if (!isMainFrame) {
        //     Log.d("WVUrlTrace", "handleUrlOverride early-return: url=" + url
        //             + ", isMainFrame=" + isMainFrame);
        //     return false;
        // }

        // 获取外部链接打开模式：2=拦截所有（默认），0=弹窗确认，1=直接跳转
        int externalLinkMode = getExternalLinkMode();

        // 所有非 http(s) 链接（自定义 scheme 如 snssdk://, intent://, weixin:// 等）
        Log.d("WVUrlTrace", "handleUrlOverride non-http url=" + url
                + ", mode=" + externalLinkMode);

        if (externalLinkMode == 0) {
            // 模式0：弹窗确认
            showExternalOpenConfirmDialog(url);
        } else if (externalLinkMode == 1) {
            // 模式1：直接跳转
            openExternalUri(url);
        }
        // 模式2（默认）：拦截所有，静默不处理
        return true;
    }

    private int getExternalLinkMode() {
        try {
            SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
            return prefs.getInt("external_link_mode", 2); // 默认拦截
        } catch (Exception ignored) {
            return 2;
        }
    }

    private void toggleExternalBlockMode() {
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        int currentMode = prefs.getInt("external_link_mode", 2);
        // 三种模式循环：2(拦截) -> 0(弹窗) -> 1(直接跳转) -> 2(拦截)
        int newMode;
        String message;
        if (currentMode == 2) {
            newMode = 0;
            message = "已切换为：弹窗确认";
        } else if (currentMode == 0) {
            newMode = 1;
            message = "已切换为：直接跳转";
        } else {
            newMode = 2;
            message = "已切换为：拦截所有外部链接";
        }
        prefs.edit().putInt("external_link_mode", newMode).apply();
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showAddTagDialog() {
        if (webView == null) {
            Toast.makeText(this, "页面未加载", Toast.LENGTH_SHORT).show();
            return;
        }
        String url = webView.getUrl();
        if (url == null || url.isEmpty()) {
            Toast.makeText(this, "无法获取页面URL", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_tag, null);
        EditText input = dialogView.findViewById(R.id.edit_tag_input);
        FlexboxLayout recentTagsContainer = dialogView.findViewById(R.id.recent_tags_container);
        FlexboxLayout currentTagsContainer = dialogView.findViewById(R.id.current_tags_container);
        TextView currentTagsLabel = dialogView.findViewById(R.id.text_current_tags_label);

        // 加载现有标签（异步）
        final List<String> existingTags = new ArrayList<>();
        final boolean[] linkExists = {false};
        final long[] linkId = {-1};

        new Thread(() -> {
            LinkDao dao = new LinkDao(DbConnection.get(WebViewActivity.this).writable());
            try {
                if (dao.urlExists(url)) {
                    linkExists[0] = true;
                    List<LinkItem> allLinks = dao.getAllLinks();
                    for (LinkItem link : allLinks) {
                        if (url.equals(link.getUrl())) {
                            existingTags.addAll(link.getTags());
                            linkId[0] = link.getId();
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                // 共享 DbConnection 连接,不关闭
            }

            runOnUiThread(() -> {
                // 显示当前标签
                if (!existingTags.isEmpty()) {
                    currentTagsLabel.setVisibility(View.VISIBLE);
                    for (String tag : existingTags) {
                        TextView tagView = (TextView) LayoutInflater.from(WebViewActivity.this)
                                .inflate(R.layout.item_tag, currentTagsContainer, false);
                        tagView.setText(tag);
                        tagView.setOnClickListener(v -> {
                            // 点击删除：从容器中移除，并从列表中移除
                            currentTagsContainer.removeView(tagView);
                            existingTags.remove(tag);
                        });
                        currentTagsContainer.addView(tagView);
                    }
                }

                // 显示最近标签
                List<String> recentTags = RecentTagsManager.getRecentTags(WebViewActivity.this);
                if (!recentTags.isEmpty()) {
                    TextView recentTagsLabel = dialogView.findViewById(R.id.text_recent_tags_label);
                    recentTagsLabel.setVisibility(View.VISIBLE);
                    for (String tag : recentTags) {
                        TextView tagView = (TextView) LayoutInflater.from(WebViewActivity.this)
                                .inflate(R.layout.item_recent_tag, recentTagsContainer, false);
                        tagView.setText(tag);
                        tagView.setOnClickListener(v -> {
                            String currentText = input.getText().toString().trim();
                            if (!currentText.isEmpty() && !currentText.endsWith(",") && !currentText.endsWith("，")) {
                                input.setText(currentText + "，" + tag);
                            } else {
                                input.setText(currentText + tag);
                            }
                            input.setSelection(input.getText().length());
                        });
                        recentTagsContainer.addView(tagView);
                    }
                }
            });
        }).start();

        new AlertDialog.Builder(this)
                .setTitle("添加标签")
                .setView(dialogView)
                .setPositiveButton("确定", (dialogInterface, which) -> {
                    String tagName = input.getText().toString().trim();
                    String[] tags = tagName.split("[,，]");
                    List<String> newTags = new ArrayList<>();
                    for (String tag : tags) {
                        String trimmedTag = tag.trim();
                        if (!trimmedTag.isEmpty()) {
                            newTags.add(trimmedTag);
                        }
                    }

                    List<String> allTags = new ArrayList<>(existingTags);
                    allTags.addAll(newTags);

                    if (!allTags.isEmpty()) {
                        if (!newTags.isEmpty()) {
                            RecentTagsManager.addRecentTags(this, newTags);
                        }
                        saveTagsToLink(url, allTags);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void saveTagsToLink(String url, List<String> tags) {
        new Thread(() -> {
            LinkDao dao = new LinkDao(DbConnection.get(WebViewActivity.this).writable());
            try {
                LinkItem item;

                if (dao.urlExists(url)) {
                    List<LinkItem> allLinks = dao.getAllLinks();
                    item = null;
                    for (LinkItem link : allLinks) {
                        if (url.equals(link.getUrl())) {
                            item = link;
                            break;
                        }
                    }
                } else {
                    String title = pageTitleCache != null && !pageTitleCache.isEmpty()
                            ? pageTitleCache : "未命名";
                    item = new LinkItem(title, url, "", "", "");
                    item.setClickCount(0);
                    item.setSummary("");
                    long id = dao.insertLink(item);
                    item.setId(id);
                }

                if (item != null) {
                    for (String tag : tags) {
                        item.addTag(tag);
                    }
                    dao.updateLinkTags(item);
                    runOnUiThread(() -> Toast.makeText(WebViewActivity.this,
                            "已保存标签: " + String.join(", ", tags), Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                Log.e("WebViewActivity", "保存标签失败: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(WebViewActivity.this,
                        "保存标签失败", Toast.LENGTH_SHORT).show());
            } finally {
                // 共享 DbConnection 连接,不关闭
            }
        }).start();
    }

    private void showExternalOpenConfirmDialog(String url) {
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle("打开外部应用")
                .setMessage("网页正在尝试打开外部应用，是否继续？\n\n" + url)
                .setNegativeButton("留在网页", null)
                .setPositiveButton("继续打开", (dialog, which) -> openExternalUri(url))
                .show());
    }

    private void openExternalUri(String url) {
        try {
            Intent intent;
            if (url.startsWith("intent://")) {
                // 解析 intent:// URI
                intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setComponent(null);
                intent.setSelector(null);
                Log.d("WVUrlTrace", "openExternalUri intent:// parsed: action=" + intent.getAction()
                        + ", data=" + intent.getData()
                        + ", package=" + intent.getPackage()
                        + ", categories=" + intent.getCategories()
                        + ", extras=" + intent.getExtras());
            } else {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                Log.d("WVUrlTrace", "openExternalUri ACTION_VIEW url=" + url);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Log.e("WVUrlTrace", "openExternalUri failed for url=" + url, e);
            Toast.makeText(WebViewActivity.this, "无法打开此链接: " + url, Toast.LENGTH_SHORT).show();
        }
    }

    

    @Override
    protected void onPause() {
        isAppInForeground = false;
        // 无条件启动前台服务保活进程，不请求 AudioFocus（WebView 自己管理焦点）
        Intent serviceIntent = new Intent(this, WebViewBackgroundService.class);
        serviceIntent.putExtra("current_url", currentUrl);
        startForegroundService(serviceIntent);
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(30 * 60 * 1000L);
        }
        super.onPause();
    }

    private void initAudio() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(focusChange -> {
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS
                            || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        if (webView != null) {
                            webView.evaluateJavascript(
                                "(function(){[].slice.call(document.getElementsByTagName('audio'))"
                                + ".concat([].slice.call(document.getElementsByTagName('video')))"
                                + ".forEach(function(e){e.pause();});})()", null);
                        }
                    } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                        if (audioPlaying && webView != null) {
                            webView.evaluateJavascript(
                                "(function(){[].slice.call(document.getElementsByTagName('audio'))"
                                + ".concat([].slice.call(document.getElementsByTagName('video')))"
                                + ".forEach(function(e){e.play().catch(function(){});});})()", null);
                        }
                    }
                })
                .build();

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WebViewAudio::WakeLock");

        mediaSession = new MediaSessionCompat(this, "WebViewAudio");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                if (webView != null) {
                    webView.evaluateJavascript("if(window.__playMedia)window.__playMedia()", null);
                }
            }
            @Override
            public void onPause() {
                if (webView != null) {
                    webView.evaluateJavascript("if(window.__pauseMedia)window.__pauseMedia()", null);
                }
            }
            @Override
            public void onStop() {
                stopAudioPlayback();
            }
        });
        mediaSession.setActive(true);
        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_PAUSE
                        | PlaybackStateCompat.ACTION_PLAY_PAUSE
                        | PlaybackStateCompat.ACTION_STOP)
                .setState(PlaybackStateCompat.STATE_NONE,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .build());

        // 注册静态回调，让 Service 能直接调用我们的方法（同进程，无需广播或 IPC）
        WebViewBackgroundService.setMediaCallback(new WebViewBackgroundService.MediaCallback() {
            @Override
            public void onPlayRequested() {
                runOnUiThread(() -> {
                    if (webView != null) {
                        webView.evaluateJavascript("if(window.__playMedia)window.__playMedia()", null);
                    }
                });
            }
            @Override
            public void onPauseRequested() {
                runOnUiThread(() -> {
                    if (webView != null) {
                        webView.evaluateJavascript("if(window.__pauseMedia)window.__pauseMedia()", null);
                    }
                });
            }
            @Override
            public void onStopRequested() {
                runOnUiThread(() -> {
                    if (webView != null) {
                        webView.evaluateJavascript("if(window.__stopMedia)window.__stopMedia()", null);
                    }
                });
            }
        });
    }

    private void onMediaPlayingChanged(boolean isPlaying) {
        audioPlaying = isPlaying;
        if (mediaSession != null) {
            mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                    .setActions(PlaybackStateCompat.ACTION_PLAY
                            | PlaybackStateCompat.ACTION_PAUSE
                            | PlaybackStateCompat.ACTION_PLAY_PAUSE)
                    .setState(isPlaying ? PlaybackStateCompat.STATE_PLAYING
                                       : PlaybackStateCompat.STATE_PAUSED,
                            PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                    .build());
        }
        // 不在这里请求/放弃 AudioFocus，WebView 自己管理音频焦点。
        // 不在后台时因为播放状态变化停止服务，防止瞬时波动导致服务被杀。
        // 服务的停止统一由 onResume 和 onDestroy 管理。
        if (!isPlaying && isAppInForeground) {
            // 前台音频停止播放时，可以停止服务
            stopService(new Intent(WebViewActivity.this, WebViewBackgroundService.class));
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }

    // --- Continuous-reading helpers ---
    private void parseNavigationContext() {
        try {
            Intent it = getIntent();
            if (it != null && it.hasExtra("context_ids")) {
                contextIds = it.getLongArrayExtra("context_ids");
                currentIndex = it.getIntExtra("context_index", 0);
                Log.d("WebViewActivity", "parseNavigationContext: contextIds.length=" + contextIds.length + ", currentIndex=" + currentIndex);
            } else {
                contextIds = null;
                currentIndex = -1;
                Log.d("WebViewActivity", "parseNavigationContext: no context_ids in intent");
            }
        } catch (Exception e) {
            Log.w("WebViewActivity", "parseNavigationContext failed: " + e.getMessage());
            contextIds = null;
            currentIndex = -1;
        }
    }

    private boolean hasValidNavigationContext() {
        return contextIds != null && contextIds.length > 1
                && currentIndex >= 0 && currentIndex < contextIds.length;
    }

    private void setupNavigationControls() {
        try {
            navigationControls = findViewById(R.id.navigation_controls);
            if (navigationControls == null) return;
            buttonPrevious = navigationControls.findViewById(R.id.button_previous);
            buttonNext = navigationControls.findViewById(R.id.button_next);
            if (buttonPrevious != null) buttonPrevious.setOnClickListener(v -> navigateToPrevious());
            if (buttonNext != null) buttonNext.setOnClickListener(v -> navigateToNext());
            hideControlsRunnable = () -> runOnUiThread(() -> {
                if (navigationControls != null) navigationControls.setVisibility(View.GONE);
            });
            navigationControls.setVisibility(hasValidNavigationContext() ? View.VISIBLE : View.GONE);
        } catch (Exception e) {
            Log.w("WebViewActivity", "setupNavigationControls failed: " + e.getMessage());
        }
    }

    private void initControlsIfNeeded() {
        if (navigationControls == null) setupNavigationControls();
        if (!hasValidNavigationContext()) {
            if (navigationControls != null) {
                navigationControls.setVisibility(View.GONE);
            }
            return;
        }
        // 读取用户手动隐藏的状态
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        navigationControlsManuallyHidden = prefs.getBoolean(KEY_NAV_CONTROLS_HIDDEN, false);

        boolean smoothMode = isSmoothReadingMode();
        if (navigationControls != null) {
            if (!smoothMode) {
                // 普通模式：完全不显示导航控件
                navigationControls.setVisibility(View.GONE);
            } else if (navigationControlsManuallyHidden) {
                // 丝滑模式但用户手动隐藏了，保持隐藏
                navigationControls.setVisibility(View.GONE);
            } else {
                // 丝滑模式：默认显示，3秒后自动隐藏
                navigationControls.setVisibility(View.VISIBLE);
                controlsHandler.removeCallbacks(hideControlsRunnable);
                controlsHandler.postDelayed(hideControlsRunnable, CONTROLS_AUTO_HIDE_MS);
            }
        }
    }

    private void showControlsTemporarily() {
        if (navigationControls == null || !hasValidNavigationContext()) return;
        // 如果用户手动隐藏了，不再自动显示
        if (navigationControlsManuallyHidden) return;
        navigationControls.setVisibility(View.VISIBLE);
        controlsHandler.removeCallbacks(hideControlsRunnable);
        controlsHandler.postDelayed(hideControlsRunnable, CONTROLS_AUTO_HIDE_MS);
    }

    private void toggleNavigationControls() {
        if (navigationControls == null || !hasValidNavigationContext()) return;
        navigationControlsManuallyHidden = !navigationControlsManuallyHidden;
        // 保存状态到 SharedPreferences
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_NAV_CONTROLS_HIDDEN, navigationControlsManuallyHidden).apply();
        if (navigationControlsManuallyHidden) {
            navigationControls.setVisibility(View.GONE);
            controlsHandler.removeCallbacks(hideControlsRunnable);
        } else {
            navigationControls.setVisibility(View.VISIBLE);
            controlsHandler.postDelayed(hideControlsRunnable, CONTROLS_AUTO_HIDE_MS);
        }
    }

    private boolean isSmoothReadingMode() {
        try {
            SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
            String mode = prefs.getString("reading_mode", "normal");
            return "smooth".equals(mode);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void attachControlRevealTouchListener() {
        if (webView == null) {
            return;
        }
        webView.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN
                    && hasValidNavigationContext()
                    && isSmoothReadingMode()) {
                showControlsTemporarily();
            }
            return false;
        });
    }

    private void navigateToPrevious() {
        if (!hasValidNavigationContext() || isNavigating) return;
        int previousIndex = (currentIndex - 1 + contextIds.length) % contextIds.length;
        loadByContextIndex(previousIndex);
    }

    private void navigateToNext() {
        if (!hasValidNavigationContext() || isNavigating) return;
        int nextIndex = (currentIndex + 1) % contextIds.length;
        loadByContextIndex(nextIndex);
    }

    private void loadByContextIndex(int index) {
        if (contextIds == null || index < 0 || index >= contextIds.length) return;
        if (isNavigating) return;
        isNavigating = true;
        long targetId = contextIds[index];
        new Thread(() -> {
            LinkDao dao = new LinkDao(DbConnection.get(WebViewActivity.this).writable());
            String url = null;
            String title = null;
            try {
                person.notfresh.readingshare.model.LinkItem item = dao.getLinkById(targetId);
                if (item != null) {
                    url = item.getUrl();
                    title = item.getTitle();
                }
            } catch (Exception e) {
                Log.w("WebViewActivity", "loadByContextIndex lookup failed: " + e.getMessage());
            } finally {
                // 共享 DbConnection 连接,不关闭
            }
            final String finalUrl = url;
            final String finalTitle = title;
            runOnUiThread(() -> {
                if (finalUrl != null && webView != null) {
                    if (!finalUrl.equals(currentUrl)) {
                        webView.loadUrl(finalUrl);
                        currentUrl = finalUrl;
                        if (finalTitle != null && toolbar != null) toolbar.setTitle(finalTitle);
                    }
                    currentIndex = index;
                    showControlsTemporarily();
                } else {
                    Toast.makeText(this, "无法加载下一页", Toast.LENGTH_SHORT).show();
                }
                isNavigating = false;
            });
        }).start();
    }
    // --- end continuous-reading helpers ---

    // --- 通知栏停止音频辅助 ---
    private void stopAudioPlayback() {
        if (webView != null) {
            webView.evaluateJavascript("if(window.__stopMedia)window.__stopMedia()", null);
        }
    }
    // --- end 通知栏停止音频辅助 ---

    private class MediaInterfaceObject {
        @android.webkit.JavascriptInterface
        public void setMediaPlaying(boolean isPlaying) {
            runOnUiThread(() -> onMediaPlayingChanged(isPlaying));
        }
    }
}