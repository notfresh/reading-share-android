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
import android.os.PowerManager;
import android.content.Context;
import android.os.Build;
import android.webkit.PermissionRequest;
import android.view.ViewGroup;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.util.CrawlUtil;
import person.notfresh.readingshare.util.ShareUtil;

public class WebViewActivity extends AppCompatActivity {
    private WebView webView;
    private Toolbar toolbar;
    private String currentUrl;
    private boolean audioPlaying = false;
    private MediaSessionCompat mediaSession;
    private PowerManager.WakeLock wakeLock;
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
            
            // 初始化MediaSession（确保缓存的WebView有可用的mediaSession）
            initMediaSession();
        } else {
            // 创建新的WebView
            webView = findViewById(R.id.webview);
            setupWebView();
            webView.loadUrl(currentUrl);
        }

        // 获取WakeLock
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, 
                "WebViewAudio::WakeLock");

        // 只对通义网站禁用MediaSessionCompat功能
        if (currentUrl != null && currentUrl.contains("tongyi.aliyun.com")) {
            try {
                Log.d("WebViewActivity", "检测到通义网站，禁用MediaSession功能");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            // 不初始化mediaSession，避免NPE问题
            mediaSession = null;
            // 在通义网站上禁用JavascriptInterface
            webView.removeJavascriptInterface("AndroidMediaInterface");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.webview_menu, menu);
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
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;  // 防止重复释放
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;  // 防止重复释放
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
        initControlsIfNeeded();
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
        
        // 停止前台服务
        stopService(new Intent(this, WebViewBackgroundService.class));
        
        // 如果mediaSession为null，重新初始化它
        if (mediaSession == null && webView != null) {
            initMediaSession();
        }
    }

    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_open_browser) {
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
            
            // 首先释放mediaSession，防止后续回调时出现NPE
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
        
        // 设置WebView在后台继续播放音频
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            webView.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }
        
        // 设置WebView在后台继续播放音频
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        }
        
        // 关键：允许WebView在后台播放媒体
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        
        // 更关键：确保WebView在后台不被暂停
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onShowCustomView(View view, CustomViewCallback callback) {
                    super.onShowCustomView(view, callback);
                }
                
                // 这个方法会在音频开始和停止播放时被调用
                @Override
                public void onPermissionRequest(PermissionRequest request) {
                    runOnUiThread(() -> request.grant(request.getResources()));
                }
            });
        }
        
        // 添加JS接口
        webView.addJavascriptInterface(new MediaInterfaceObject(), "AndroidMediaInterface");
        
        // 注入安全检查脚本 - 保留这部分
        webView.evaluateJavascript(
            "function safeMediaCall(callback) {" +
            "  try {" +
            "    return callback();" +
            "  } catch(e) {" +
            "    console.log('Media interface error: ' + e.message);" +
            "    return false;" +
            "  }" +
            "}", null);
        

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrlOverride(view, url, true);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request != null && request.getUrl() != null
                        ? request.getUrl().toString()
                        : null;
                boolean isMainFrame = request == null || request.isForMainFrame();
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
                
                // 只在通义网站注入特殊处理
                if (url != null && url.contains("tongyi.aliyun.com")) {
                    view.evaluateJavascript(
                        "console.log('为通义网站应用简单处理');" +
                        "window.onerror = function(msg, url, line) {" +
                        "  if(msg.indexOf('mediaSession') > -1) {" +
                        "    console.log('已拦截mediaSession错误');" +
                        "    return true;" + // 拦截错误
                        "  }" +
                        "  return false;" + // 不拦截其他错误
                        "};" +
                        // 简单替换通义可能使用的媒体API
                        "if(window.AndroidMediaInterface === undefined) {" +
                        "  window.AndroidMediaInterface = {" +
                        "    isMediaSessionActive: function() { return false; }," +
                        "    setMediaPlaying: function() { return false; }" +
                        "  };" +
                        "}", null);
                }
            }
        });
    }

    private boolean handleUrlOverride(WebView view, String url, boolean isMainFrame) {
        if (url == null || !isMainFrame) {
            return false;
        }

        // http(s) 链接直接在 WebView 内加载
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return false;
        }

        // 所有非 http(s) 链接（自定义 scheme 如 snssdk://, intent://, weixin:// 等）
        // 一律拦截并弹窗确认，防止网页强制跳转到外部应用
        Log.d("WebViewActivity", "拦截非HTTP链接: " + url + " (来自页面: " + (view != null ? view.getUrl() : currentUrl) + ")");
        showExternalOpenConfirmDialog(url);
        return true;
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
            } else {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Log.e("WebViewActivity", "无法打开外部URI: " + url, e);
            Toast.makeText(WebViewActivity.this, "无法打开此链接: " + url, Toast.LENGTH_SHORT).show();
        }
    }

    

    @Override
    protected void onPause() {
        if (isAudioPlaying() && webView != null) {  // 添加对webView的null检查
            // 启动服务
            Intent serviceIntent = new Intent(this, WebViewBackgroundService.class);
            serviceIntent.putExtra("current_url", currentUrl);
            
            // 在音频播放时，注入保持播放的脚本
            webView.evaluateJavascript(
                "var keepPlaying = function() {" +
                "  var audios = document.getElementsByTagName('audio');" +
                "  var videos = document.getElementsByTagName('video');" +
                "  for(var i=0; i<audios.length; i++) {" +
                "    if(!audios[i].paused) {" +
                "      var playPromise = audios[i].play();" +
                "      if(playPromise !== undefined) {" +
                "        playPromise.then(_ => {}).catch(e => console.log(e));" +
                "      }" +
                "    }" +
                "  }" +
                "  for(var i=0; i<videos.length; i++) {" +
                "    if(!videos[i].paused) {" +
                "      var playPromise = videos[i].play();" +
                "      if(playPromise !== undefined) {" +
                "        playPromise.then(_ => {}).catch(e => console.log(e));" +
                "      }" +
                "    }" +
                "  }" +
                "};" +
                "keepPlaying();" +
                "setInterval(keepPlaying, 500);", null);
                
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        }
        super.onPause();
    }

   

    // 优化isAudioPlaying方法
    private boolean isAudioPlaying() {
        // 如果webView已经被移除并存档，则返回false
        // TODO: 需要检测是否正在播放，这里做一个假设而已
        if (webView == null) {
            return false;
        }
        return audioPlaying;
    }

    private void initMediaSession() {
        mediaSession = new MediaSessionCompat(this, "WebViewAudio");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setActive(true);
        
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_PLAY_PAUSE);
        mediaSession.setPlaybackState(stateBuilder.build());
    }

    // --- Continuous-reading helpers ---
    private void parseNavigationContext() {
        try {
            Intent it = getIntent();
            if (it != null && it.hasExtra("context_ids")) {
                contextIds = it.getLongArrayExtra("context_ids");
                currentIndex = it.getIntExtra("context_index", 0);
            } else {
                contextIds = null;
                currentIndex = -1;
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
        boolean smoothMode = isSmoothReadingMode();
        if (navigationControls != null) {
            navigationControls.setVisibility(smoothMode ? View.GONE : View.VISIBLE);
            if (smoothMode) {
                navigationControls.setVisibility(View.VISIBLE);
                controlsHandler.removeCallbacks(hideControlsRunnable);
                controlsHandler.postDelayed(hideControlsRunnable, CONTROLS_AUTO_HIDE_MS);
            }
        }
    }

    private void showControlsTemporarily() {
        if (navigationControls == null || !hasValidNavigationContext()) return;
        navigationControls.setVisibility(View.VISIBLE);
        controlsHandler.removeCallbacks(hideControlsRunnable);
        controlsHandler.postDelayed(hideControlsRunnable, CONTROLS_AUTO_HIDE_MS);
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
            LinkDao dao = new LinkDao(this);
            String url = null;
            String title = null;
            try {
                dao.open();
                person.notfresh.readingshare.model.LinkItem item = dao.getLinkById(targetId);
                if (item != null) {
                    url = item.getUrl();
                    title = item.getTitle();
                }
            } catch (Exception e) {
                Log.w("WebViewActivity", "loadByContextIndex lookup failed: " + e.getMessage());
            } finally {
                try { dao.close(); } catch (Exception ignored) {}
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

    // 添加一个JS接口类来处理媒体操作
    private class MediaInterfaceObject {
        @android.webkit.JavascriptInterface
        public boolean isMediaSessionActive() {
            try {
                return mediaSession != null && mediaSession.isActive();
            } catch (Exception e) {
                Log.e("WebViewActivity", "MediaSession访问错误", e);
                return false;
            }
        }
        
        @android.webkit.JavascriptInterface
        public void setMediaPlaying(boolean isPlaying) {
            try {
                audioPlaying = isPlaying;
                // 安全地更新媒体状态
                if (mediaSession != null) {
                    PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder();
                    stateBuilder.setState(
                        isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                        1.0f);
                    mediaSession.setPlaybackState(stateBuilder.build());
                }
            } catch (Exception e) {
                Log.e("WebViewActivity", "设置媒体状态错误", e);
            }
        }
        
        @android.webkit.JavascriptInterface
        public boolean isTongyiSite(String url) {
            return url != null && url.contains("tongyi.aliyun.com");
        }
    }
}