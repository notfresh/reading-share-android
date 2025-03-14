package person.notfresh.readingshare;

import static person.notfresh.readingshare.WebViewManager.*;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.View;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.os.PowerManager;
import android.content.Context;
import android.os.Build;
import android.webkit.PermissionRequest;
import android.view.ViewGroup;

public class WebViewActivity extends AppCompatActivity {
    private WebView webView;
    private Toolbar toolbar;
    private String currentUrl;
    private boolean audioPlaying = false;
    private MediaSessionCompat mediaSession;
    private PowerManager.WakeLock wakeLock;
    private boolean preserveCache = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);

        // 初始化 Toolbar
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // 获取传入的URL
        currentUrl = getIntent().getStringExtra("url");
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
            webViewContainer.addView(webView);
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
            Log.d("WebViewActivity", "检测到通义网站，禁用MediaSession功能");
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
        }
        return super.onOptionsItemSelected(item);
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

    private void setupWebView() {
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
        
        // 改为一个更简单的通义网站处理方法
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                
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

    @Override
    protected void onStop() {
        // 使用setWillNotDraw(false)确保WebView即使在后台也能继续渲染
        if (webView != null) {
            webView.setWillNotDraw(false);
        }
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
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