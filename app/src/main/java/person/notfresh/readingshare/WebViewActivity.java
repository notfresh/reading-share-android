package person.notfresh.readingshare;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class WebViewActivity extends AppCompatActivity {
    private WebView webView;
    private Toolbar toolbar;
    private String currentUrl;

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
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.webview_menu, menu);
        // 添加调试代码
        MenuItem item = menu.findItem(R.id.action_open_browser);
        if (item == null) {
            Toast.makeText(this, "菜单项加载失败", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "菜单项加载成功", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_open_browser) {
            // 添加日志打印
            String menuTitle = item.getTitle().toString();
            Log.d("WebViewActivity", "点击了菜单项: " + menuTitle);
            
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(webView.getUrl()));
                startActivity(intent);
                return true;
            } catch (Exception e) {
                Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show();
                return true;
            }
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