package person.notfresh.readingshare;


import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
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
import java.util.Random;

import android.widget.EditText;
import android.widget.TextView;
import android.widget.LinearLayout;
import com.google.android.flexbox.FlexboxLayout;

import person.notfresh.readingshare.db.DbConnection;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.links.strategy.XiaohongshuSkipStrategy;
import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.dialog.AddOverrideRuleDialog;
import person.notfresh.readingshare.model.OverrideAction;
import person.notfresh.readingshare.model.WebViewOverrideRule;
import person.notfresh.readingshare.ui.webview.WebViewMenuActions;
import person.notfresh.readingshare.ui.webview.WebViewNavControls;
import person.notfresh.readingshare.util.ExternalLinkOpener;
import person.notfresh.readingshare.util.ReadingPositionStore;
import person.notfresh.readingshare.util.WebViewDefaults;
import person.notfresh.readingshare.util.WebViewOverrideRules;
import person.notfresh.readingshare.util.RecentTagsManager;
import person.notfresh.readingshare.util.TagSuggestionHelper;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.snackbar.Snackbar;

public class WebViewActivity extends AppCompatActivity
        implements AddOverrideRuleDialog.OnRuleSubmitListenerActivity,
        WebViewMenuActions.Host, WebViewMenuActions.Page {
    private WebView webView;
    private Toolbar toolbar;
    private String currentUrl;
    private boolean audioPlaying = false;
    private boolean isAppInForeground = true;
    private MediaSessionCompat mediaSession;
    private PowerManager.WakeLock wakeLock;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private String pageTitleCache = "";
    private boolean historyRecordedForPageLoad;
    private boolean isExternalOpen = false; // 是否从外部打开
    private long[] contextIds;
    private int currentIndex;
    private boolean isNavigating = false;
    private FrameLayout navigationControls;
    private Button buttonPrevious;
    private Button buttonNext;
    private FrameLayout floatingNavControls;
    private ImageButton buttonFloatingPrevious;
    private ImageButton buttonFloatingNext;
    private ImageButton buttonFloatingRandom;
    private Random random = new Random();
    private static final int CONTROLS_AUTO_HIDE_MS = 3000;
    private WebViewNavControls navControls;
    /** 菜单动作统一走这一份(目标2);菜单资源 res/menu/webview_menu.xml。 */
    private WebViewMenuActions menuActions;

    private static final String PREFS_NAME = "settings";
    private static final String KEY_NAV_CONTROLS_HIDDEN = "nav_controls_hidden";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);

        parseNavigationContext();
        setupNavigationControls();
        initControlsIfNeeded();
        menuActions = new WebViewMenuActions(this, this);

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

        // 2.3.20:不再寄存 WebView 实例(见 WebViewManager 删除说明),
        // 每次打开都是新实例 + 系统缓存策略(WebViewDefaults.LOAD_CACHE_ELSE_NETWORK),
        // 阅读位置由 ReadingPositionStore 记住并在 onPageFinished 里恢复。
        webView = findViewById(R.id.webview);
        setupWebView();
        webView.loadUrl(currentUrl);

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
        MenuItem toggleItem = menu.findItem(R.id.action_wv_toggle_navigation);
        if (toggleItem != null && !isSmoothReadingMode()) {
            toggleItem.setVisible(false);
        }

        // 拦截模式图标染色:红=拦截 / 白=跳转 / 灰=弹窗
        menuActions.tintBlockIcon(menu);

        return true;
    }

    @Override
    public void onBackPressed() {
        // 如果 WebView 可以后退，则后退
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            returnToMainAndRemoveTask();
        }
    }

    private void returnToMainAndRemoveTask() {
        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        mainIntent.putExtra("navigate_to", "home");
        startActivity(mainIntent);
        // WebView 是独立 document task，普通退出时必须移除任务卡片，不能回手机桌面。
        finishAndRemoveTask();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        Log.d("WebViewActivity", "onDestroy webView=" + (webView == null ? "null" : "not null"));
        // 清理 WebView 实例;但**不清**磁盘/HTTP 缓存 —— 那是"下次打开快"的唯一来源
        if (webView != null) {
            webView.stopLoading();
            webView.clearHistory();
            webView.destroy();
        }
        stopService(new Intent(this, WebViewBackgroundService.class));
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
        // 回到前台,前台保活服务不再需要(进程已在前台)
        stopService(new Intent(this, WebViewBackgroundService.class));
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // 翻页控件开关只有 WebView 页有(阅读页那份在 ReaderFragment 自己处理)
        if (item.getItemId() == R.id.action_wv_toggle_navigation) {
            toggleNavigationControls();
            return true;
        }
        if (menuActions != null && menuActions.handle(item.getItemId())) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---- WebViewMenuActions.Host ----

    @Override
    public Context context() {
        return this;
    }

    /** Activity 不存在 detach,只要没被 finish 就算活着。 */
    @Override
    public boolean isAlive() {
        return !isFinishing() && !isDestroyed();
    }

    @Override
    public void showOverrideRuleDialog(String contextUrl) {
        AddOverrideRuleDialog.showDialog(getSupportFragmentManager(), contextUrl);
    }

    @Override
    public void invalidateMenu() {
        invalidateOptionsMenu();
    }

    // ---- WebViewMenuActions.Page ----

    @Override
    public WebView webView() {
        return webView;
    }

    /**
     * 当前页面对应的链接 id。
     *
     * <p>2.3.21.8 起不再恒为 null：Activity 启动时带的 context_ids/current_index 就是
     * 「我正在读队列里第几条链接」，这是唯一可靠的身份来源。返回它之后，
     * 「加标签 / 改标题」都按主键定位，页面 302 跳到哪儿都不影响。</p>
     *
     * <p>没有上下文（外部打开 / 单条链接）时仍为 null，弹窗退回按 URL 解析
     * （{@code AddTagDialog} 内部会再试一次 originalUrl）。</p>
     */
    @Override
    public Long linkId() {
        if (contextIds != null && currentIndex >= 0 && currentIndex < contextIds.length) {
            long id = contextIds[currentIndex];
            return id > 0 ? id : null;
        }
        return null;
    }

    @Override
    public String url() {
        if (webView != null && webView.getUrl() != null && !webView.getUrl().isEmpty()) {
            return webView.getUrl();
        }
        return currentUrl;
    }

    /** 重定向前原 URL 优先(§27.4),退回 currentUrl。 */
    @Override
    public String originalUrl() {
        if (webView != null && webView.getOriginalUrl() != null
                && !webView.getOriginalUrl().isEmpty()) {
            return webView.getOriginalUrl();
        }
        return currentUrl;
    }

    @Override
    public String title() {
        if (pageTitleCache != null && !pageTitleCache.isEmpty()) return pageTitleCache;
        return webView != null ? webView.getTitle() : "";
    }

    /** 改标题成功后同步回 pageTitleCache,分享/再次编辑时不再显示旧标题。 */
    @Override
    public void onTitleUpdated(String newTitle) {
        pageTitleCache = newTitle;
    }

    private void setupWebView() {
        attachControlRevealTouchListener();

        // 基础设置 + 缓存策略统一在 WebViewDefaults（阅读页也用这一份，2.3.20）
        WebViewDefaults.apply(webView);

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
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (view == webView) {
                    historyRecordedForPageLoad = false;
                }
            }

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
                recordLinkHistory(view, url);
                restoreReadingPosition(view, url);
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

    private void recordLinkHistory(WebView view, String url) {
        if (view != webView || url == null || url.trim().isEmpty()
                || "about:blank".equalsIgnoreCase(url.trim())) {
            return;
        }
        if (historyRecordedForPageLoad) {
            return;
        }
        historyRecordedForPageLoad = true;

        String webTitle = view.getTitle();
        String cachedTitle = pageTitleCache;
        String originalUrl = currentUrl;
        final String finalUrl = url.trim();
        new Thread(() -> {
            try {
                LinkDao dao = new LinkDao(DbConnection.get(WebViewActivity.this).writable());
                String title = null;
                if (isXshlinkUrl(originalUrl)) {
                    title = dao.getLinkTitleByUrl(originalUrl);
                }
                if (title == null || title.trim().isEmpty()) {
                    title = dao.getLinkTitleByUrl(finalUrl);
                }
                if (title == null || title.trim().isEmpty()) {
                    title = webTitle;
                }
                if (title == null || title.trim().isEmpty()) {
                    title = cachedTitle;
                }
                if (title == null || title.trim().isEmpty()) {
                    title = finalUrl;
                }
                dao.insertLinkHistory(title.trim(), finalUrl, System.currentTimeMillis());
            } catch (Exception e) {
                Log.e("WebViewActivity", "记录浏览历史失败", e);
            }
        }).start();
    }

    private boolean isXshlinkUrl(String url) {
        // 单一来源:XiaohongshuSkipStrategy.isXiaohongshuUrl
        // (命名短链系列 + xiaohongshu.com 主域及子域)
        return XiaohongshuSkipStrategy.isXiaohongshuUrl(url);
    }

    /**
         * AddOverrideRuleDialog 父 Activity 回调(WebViewActivity 入口):
         * 写一条新规则到 SP,Snackbar 提示成功。
         */
        @Override
        public void onRuleSubmittedFromActivity(WebViewOverrideRule rule) {
            try {
                WebViewOverrideRules.add(this, rule);
                Snackbar.make(findViewById(android.R.id.content),
                        "已添加拦截规则: " + rule.getContextUrl() + " (" + rule.getAction().name() + ")",
                        Snackbar.LENGTH_SHORT).show();
            } catch (IllegalArgumentException e) {
                Snackbar.make(findViewById(android.R.id.content),
                        e.getMessage(), Snackbar.LENGTH_SHORT).show();
            }
        }

        /**
         * 处理 WebView 跳转白名单。命中 OverrideAction 时复用既有处理路径
         * (showExternalOpenConfirmDialog / openExternalUri / return true),未命中走原
         * {@link #getExternalLinkMode()} 全局三档逻辑。
         */
        private void applyRuleAction(String url, OverrideAction action) {
            switch (action) {
                case ASK:
                    showExternalOpenConfirmDialog(url);
                    break;
                case OPEN:
                    openExternalUri(url);
                    break;
                case BLOCK:
                    // mode=2 等价:什么都不做就 return true,见 handleUrlOverride 末尾行为
                    break;
            }
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

        // 拦截名单(白名单)优先 —— 命中规则走 OverrideAction,未命中走原 global mode
        // 匹配候选 = webview 当前页 URL(getUrl,重定向后的真页) + webview 原 URL(getOriginalUrl,302 之前的原始链)
        // 同一规则里 contextUrl(用户手填的原始)与 redirectedUrl(系统自动记录的)任一被 candidate 命中即胜出
        if (webView != null) {
            String currentPageUrl = webView.getUrl();
            String originalPageUrl = webView.getOriginalUrl();
            java.util.List<String> candidates = new java.util.ArrayList<>(2);
            if (currentPageUrl != null && !currentPageUrl.isEmpty()) candidates.add(currentPageUrl);
            if (originalPageUrl != null && !originalPageUrl.isEmpty()) candidates.add(originalPageUrl);
            Optional<WebViewOverrideRule> hit = WebViewOverrideRules.firstMatch(this, candidates);
            if (hit.isPresent()) {
                Log.d("WVUrlTrace", "handleUrlOverride rule hit candidates=" + candidates
                        + " contextUrl=" + hit.get().getContextUrl()
                        + " redirectedUrl=" + hit.get().getRedirectedUrl()
                        + " action=" + hit.get().getAction());
                String ruleContext = hit.get().getContextUrl();
                if (currentPageUrl != null && !currentPageUrl.isEmpty()
                        && !currentPageUrl.equalsIgnoreCase(ruleContext)) {
                    // 重定向场景:把 candidate(重定向后的真页)写入规则的 redirectedUrl,
                    // 之后匹配走该字段,即便 contextUrl 是短链也命中
                    WebViewOverrideRules.recordRedirectedUrl(this, ruleContext, currentPageUrl);
                }
                applyRuleAction(url, hit.get().getAction());
                // 命中后永远 return true — webview 不再尝试加载 scheme,
                // 否则 chrome 内核会再次触发 ERR_UNKNOWN_URL_SCHEME 渲染错误页
                return true;
            }
        }

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

    /** 直接 set 拦截模式到指定 mode(不走循环)。被弹窗「拒绝并禁止弹窗」调用 setExternalLinkMode(2)。 */
    private void setExternalLinkMode(int mode) {
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        prefs.edit().putInt("external_link_mode", mode).apply();
        Toast.makeText(this, "已切换为:拦截所有外部链接", Toast.LENGTH_SHORT).show();
        invalidateOptionsMenu();
    }

    private void showExternalOpenConfirmDialog(String url) {
        // 2.3.20.1:与阅读页共用同一份确认弹窗(支持中英、两按钮+勾选,不会因文案变长挤成竖排)
        runOnUiThread(() -> ExternalLinkOpener.confirm(this, url,
                () -> setExternalLinkMode(ExternalLinkOpener.MODE_BLOCK)));
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
        saveReadingPosition();
        // 退后台/锁屏期间由前台服务 + WakeLock 保活,保证 WebView 里的音频不断。
        // 不请求 AudioFocus(WebView 自己管理焦点)。
        Intent serviceIntent = new Intent(this, WebViewBackgroundService.class);
        startForegroundService(serviceIntent);
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(30 * 60 * 1000L);
        }
        super.onPause();
    }

    /** 记录当前页面的阅读进度(0..1)，onPause 时调用。 */
    private void saveReadingPosition() {
        try {
            if (webView == null) return;
            String url = webView.getUrl();
            if (url == null || url.isEmpty()) return;
            float contentHeight = webView.getContentHeight() * getResources().getDisplayMetrics().density;
            if (contentHeight <= 0f) return;
            float percent = Math.max(0f, Math.min(1f, webView.getScrollY() / contentHeight));
            ReadingPositionStore.save(this, url, percent);
        } catch (Exception e) {
            Log.w("WVPosition", "save reading position failed", e);
        }
    }

    /** 页面加载完成后滚回上次位置；没有记录就不动。 */
    private void restoreReadingPosition(WebView view, String url) {
        try {
            if (view == null || url == null || url.isEmpty()) return;
            float percent = ReadingPositionStore.load(this, url);
            if (percent <= 0f) return;
            // 图片懒加载在 onPageFinished 之后还会长高，用 getContentHeight 现算再滚
            view.post(() -> {
                try {
                    float contentHeight = view.getContentHeight() * getResources().getDisplayMetrics().density;
                    if (contentHeight > 0f) {
                        view.scrollTo(0, (int) (contentHeight * percent));
                    }
                } catch (Exception ignored) {
                    // 恢复失败不影响阅读
                }
            });
        } catch (Exception e) {
            Log.w("WVPosition", "restore reading position failed", e);
        }
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
            if (navigationControls != null) {
                buttonPrevious = navigationControls.findViewById(R.id.button_previous);
                buttonNext = navigationControls.findViewById(R.id.button_next);
                if (buttonPrevious != null) buttonPrevious.setOnClickListener(v -> navigateToPrevious());
                if (buttonNext != null) buttonNext.setOnClickListener(v -> navigateToNext());
            }

            floatingNavControls = findViewById(R.id.floating_nav_controls);
            if (floatingNavControls != null) {
                buttonFloatingPrevious = floatingNavControls.findViewById(R.id.button_floating_previous);
                buttonFloatingNext = floatingNavControls.findViewById(R.id.button_floating_next);
                buttonFloatingRandom = floatingNavControls.findViewById(R.id.button_floating_random);
                if (buttonFloatingPrevious != null) buttonFloatingPrevious.setOnClickListener(v -> navigateToPrevious());
                if (buttonFloatingNext != null) buttonFloatingNext.setOnClickListener(v -> navigateToNext());
                if (buttonFloatingRandom != null) buttonFloatingRandom.setOnClickListener(v -> navigateToRandom());
            }

            // 显隐策略统一由 WebViewNavControls 负责(与阅读页同一份实现)
            navControls = new WebViewNavControls(navigationControls, floatingNavControls);
        } catch (Exception e) {
            Log.w("WebViewActivity", "setupNavigationControls failed: " + e.getMessage());
        }
    }

    private void initControlsIfNeeded() {
        if (navControls == null) setupNavigationControls();
        if (navControls != null) {
            navControls.setContentAvailable(this, hasValidNavigationContext());
        }
    }

    private void showControlsTemporarily() {
        if (navControls != null) navControls.onWebViewTouched(this);
    }

    private void toggleNavigationControls() {
        if (navControls != null) navControls.toggleManually(this);
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
        if (webView == null) return;
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

    private void navigateToRandom() {
        if (!hasValidNavigationContext() || isNavigating) return;
        int length = contextIds.length;
        int targetIndex;
        do {
            targetIndex = random.nextInt(length);
        } while (targetIndex == currentIndex);
        loadByContextIndex(targetIndex);
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