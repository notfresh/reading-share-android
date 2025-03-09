package person.notfresh.readingshare;

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

public class WebViewActivity extends AppCompatActivity {
    private WebView webView;
    private Toolbar toolbar;
    private String currentUrl;
    private boolean audioPlaying = false;
    private MediaSessionCompat mediaSession;
    private PowerManager.WakeLock wakeLock;

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

        // 初始化 WebView
        webView = findViewById(R.id.webview);
        setupWebView();
        
        // 加载URL
        webView.loadUrl(currentUrl);

        // 初始化MediaSession
        initMediaSession();
        
        // 获取WakeLock
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, 
                "WebViewAudio::WakeLock");
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
        }
        return super.onOptionsItemSelected(item);
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
        
        // 修改JavaScriptInterface
        webView.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void setAudioPlaying(boolean isPlaying) {
                audioPlaying = isPlaying;
                if (isPlaying) {
                    // 当音频开始播放时激活MediaSession
                    if (!mediaSession.isActive()) {
                        mediaSession.setActive(true);
                    }
                    // 获取WakeLock以保持CPU运行
                    if (!wakeLock.isHeld()) {
                        wakeLock.acquire(3600*1000); // 持有1小时
                    }
                } else {
                    // 当音频停止时释放资源
                    if (mediaSession.isActive()) {
                        mediaSession.setActive(false);
                    }
                    if (wakeLock.isHeld()) {
                        wakeLock.release();
                    }
                }
            }
        }, "AndroidAudio");
        
        // 设置 WebViewClient 处理页面加载
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                currentUrl = url;
                // 如果是特殊的 scheme，使用系统处理
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                        return true;
                    } catch (Exception e) {
                        Toast.makeText(WebViewActivity.this, 
                            "无法打开此链接", 
                            Toast.LENGTH_SHORT).show();
                        return true;
                    }
                }
                
                // 普通网页链接在WebView中加载
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // 注入JavaScript来监听音频/视频播放状态
                String js = "javascript:" +
                        "var audioElements = document.getElementsByTagName('audio');" +
                        "var videoElements = document.getElementsByTagName('video');" +
                        "function updatePlayingStatus() {" +
                        "  var isPlaying = false;" +
                        "  for(var i=0; i<audioElements.length; i++) {" +
                        "    if(!audioElements[i].paused) { isPlaying = true; break; }" +
                        "  }" +
                        "  if(!isPlaying) {" +
                        "    for(var i=0; i<videoElements.length; i++) {" +
                        "      if(!videoElements[i].paused) { isPlaying = true; break; }" +
                        "    }" +
                        "  }" +
                        "  AndroidAudio.setAudioPlaying(isPlaying);" +
                        "}" +
                        "setInterval(updatePlayingStatus, 1000);" +
                        "for(var i=0; i<audioElements.length; i++) {" +
                        "  audioElements[i].addEventListener('play', function() { AndroidAudio.setAudioPlaying(true); });" +
                        "  audioElements[i].addEventListener('pause', function() { updatePlayingStatus(); });" +
                        "}" +
                        "for(var i=0; i<videoElements.length; i++) {" +
                        "  videoElements[i].addEventListener('play', function() { AndroidAudio.setAudioPlaying(true); });" +
                        "  videoElements[i].addEventListener('pause', function() { updatePlayingStatus(); });" +
                        "}";
                view.evaluateJavascript(js, null);
            }
        });

        // 设置 WebChromeClient 处理进度和标题
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(title);
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
        // 清理 WebView
        if (webView != null) {
            webView.stopLoading();
            webView.clearHistory();
            webView.clearCache(true);
            webView.destroy();
        }
        if (mediaSession != null) {
            mediaSession.release();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        if (isAudioPlaying()) {
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
    }

    // 优化isAudioPlaying方法
    private boolean isAudioPlaying() {
        // 这个方法需要您根据应用的具体情况来实现
        // 可以使用JavaScript接口来检测网页中的音频状态
        //return true; // 为了简单起见，这里假设总是有音频在播放
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
} 