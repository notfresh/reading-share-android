package person.notfresh.readingshare;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class WebViewActivity extends AppCompatActivity {
    private WebView webView;
    private Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);

        // 初始化 Toolbar
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // 获取传入的URL
        String url = getIntent().getStringExtra("url");
        if (url == null || url.isEmpty()) {
            Toast.makeText(this, "无效的URL", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 初始化 WebView
        webView = findViewById(R.id.webview);
        setupWebView();
        
        // 加载URL
        webView.loadUrl(url);
    }

    private void setupWebView() {
        // 启用 JavaScript
        webView.getSettings().setJavaScriptEnabled(true);
        
        // 设置缩放控制
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        
        // 启用 DOM storage
        webView.getSettings().setDomStorageEnabled(true);
        
        // 设置 WebViewClient 处理页面加载
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
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
        super.onDestroy();
    }
} 