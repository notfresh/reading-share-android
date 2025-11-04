package person.notfresh.readingshare;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 专门处理快捷方式跳转的Activity
 * 接收快捷方式的Intent，提取URL并打开WebView
 */
public class WebShortcutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 从Intent中获取URL
        String url = null;
        Intent intent = getIntent();
        
        if (intent != null) {
            // 优先从extra中获取
            url = intent.getStringExtra("url");
            
            // 如果没有，尝试从data URI中获取
            if (url == null || url.isEmpty()) {
                Uri data = intent.getData();
                if (data != null) {
                    url = data.toString();
                }
            }
        }
        
        if (url == null || url.isEmpty()) {
            Toast.makeText(this, "无效的URL", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // 打开WebViewActivity
        Intent webViewIntent = new Intent(this, WebViewActivity.class);
        webViewIntent.putExtra("url", url);
        startActivity(webViewIntent);
        
        // 关闭当前Activity
        finish();
    }
}

