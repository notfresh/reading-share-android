package person.notfresh.myapplication;

import android.os.Bundle;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.content.Intent;
import android.net.Uri;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import android.util.Log;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.navigation.NavigationView;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import person.notfresh.myapplication.databinding.ActivityMainBinding;
import person.notfresh.myapplication.db.LinkDao;
import person.notfresh.myapplication.model.LinkItem;
import person.notfresh.myapplication.adapter.LinksAdapter;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.app.AlertDialog;
import android.widget.EditText;
import android.text.TextUtils;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.content.Context;
import android.Manifest;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.app.ActivityManager;
import androidx.annotation.NonNull;
import java.net.URLConnection;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;
    private LinkDao linkDao;
    private static final int CLIPBOARD_PERMISSION_REQUEST = 100;
    private boolean hasFocus = false;
    private String lastProcessedClipText = "";  // 添加这个变量来记录上次处理的剪贴板内容

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        linkDao = new LinkDao(this);
        linkDao.open();

        setSupportActionBar(binding.appBarMain.toolbar);
        
        handleIntent(getIntent());

        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow)
                .setOpenableLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        checkClipboardPermission();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        this.hasFocus = hasFocus;
        if (hasFocus) {
            // 当窗口真正获得焦点时检查剪贴板
            checkClipboard();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 不在这里检查剪贴板，改为在获得焦点时检查
        // checkClipboardPermission();
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                String title = intent.getStringExtra(Intent.EXTRA_SUBJECT);
                
                // 打印Intent信息，帮助调试
                Log.d("ShareIntent", "Action: " + action);
                Log.d("ShareIntent", "Type: " + type);
                Log.d("ShareIntent", "Text: " + sharedText);
                Log.d("ShareIntent", "Subject: " + title);
                if (intent.getComponent() != null) {
                    Log.d("ShareIntent", "Package: " + intent.getComponent().getPackageName());
                }
                Log.d("ShareIntent", "Data: " + intent.getDataString());
                Bundle extras = intent.getExtras();
                if (extras != null) {
                    for (String key : extras.keySet()) {
                        Log.d("ShareIntent", "Extra: " + key + " = " + extras.get(key));
                    }
                }
                
                // 打印更详细的Intent信息
                Log.d("ShareIntent", "=== 分享详细信息 ===");
                Log.d("ShareIntent", "Intent: " + intent.toString());
                Log.d("ShareIntent", "Categories: " + intent.getCategories());
                Log.d("ShareIntent", "Flags: " + Integer.toHexString(intent.getFlags()));
                Log.d("ShareIntent", "Scheme: " + intent.getScheme());
                
                if (intent.getComponent() != null) {
                    Log.d("ShareIntent", "Component: " + intent.getComponent().flattenToString());
                }
                
                // 打印所有可以处理这个Intent的应用
                PackageManager pm = getPackageManager();
                List<ResolveInfo> activities = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
                for (ResolveInfo info : activities) {
                    Log.d("ShareIntent", "可处理的应用: " + info.activityInfo.packageName);
                    Log.d("ShareIntent", "Activity: " + info.activityInfo.name);
                }
                
                if (sharedText != null) {
                    // 获取分享来源的应用包名
                    String sourceApp = "";
                    if (intent.getComponent() != null) {
                        sourceApp = intent.getComponent().getPackageName();
                    }

                    // 保存完整的分享信息
                    if (title == null || title.isEmpty()) {
                        // 如果没有标题，尝试从分享文本中提取
                        title = extractTitle(sharedText);
                    }

                    // 收集目标Activity信息
                    StringBuilder targetActivityInfo = new StringBuilder();
                    for (ResolveInfo info : activities) {
                        targetActivityInfo.append(info.activityInfo.packageName)
                                         .append("/")
                                         .append(info.activityInfo.name)
                                         .append(";");
                    }

                    // 保存更详细的信息
                    LinkItem newLink = new LinkItem(
                        title,
                        sharedText,
                        sourceApp,
                        intent.toString(),  // 保存完整的Intent信息
                        targetActivityInfo.toString()  // 保存可以处理的Activity信息
                    );

                    linkDao.insertLink(newLink);

                    NavController navController = Navigation.findNavController(this, 
                            R.id.nav_host_fragment_content_main);
                    navController.navigate(R.id.nav_home);
                    
                    Snackbar.make(binding.getRoot(), "已保存：" + title, 
                            Snackbar.LENGTH_LONG).show();
                }
            }
        }
    }

    // 从分享内容中提取标题
    private String extractTitle(String text) {
        // 移除 URL
        String[] parts = text.split("\\s+");
        StringBuilder title = new StringBuilder();
        
        for (String part : parts) {
            if (!part.startsWith("http://") && !part.startsWith("https://")) {
                if (title.length() > 0) {
                    title.append(" ");
                }
                title.append(part);
            }
        }
        
        String result = title.toString().trim();
        return result.isEmpty() ? "分享的内容" : result;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (linkDao != null) {
            linkDao.close();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 让 Fragment 处理菜单
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // 先让 Fragment 处理菜单项点击
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_content_main);
        if (currentFragment != null && currentFragment.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    private void checkClipboardPermission() {
        // 直接检查剪贴板，不需要请求权限
        checkClipboard();
    }

    private void checkClipboard() {
        try {
            // 确保应用真正在前台并且有焦点
            if (!hasFocus || !isAppInForeground()) {
                Log.d("Clipboard", "App is not in focus or foreground");
                return;
            }

            // 添加短暂延迟，确保应用完全获得焦点
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    Log.d("Clipboard", "Checking clipboard...");
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    Log.d("Clipboard", "Clipboard manager: " + (clipboard != null));
                    
                    if (clipboard != null && clipboard.hasPrimaryClip()) {
                        ClipData clipData = clipboard.getPrimaryClip();
                        if (clipData != null && clipData.getItemCount() > 0) {
                            ClipData.Item item = clipData.getItemAt(0);
                            CharSequence text = item.getText();
                            
                            if (text != null) {
                                handleClipboardText(text.toString());
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e("Clipboard", "Error checking clipboard", e);
                    e.printStackTrace();
                }
            }, 500);

        } catch (Exception e) {
            Log.e("Clipboard", "Error in checkClipboard", e);
            e.printStackTrace();
        }
    }

    private boolean isAppInForeground() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
        if (appProcesses == null) return false;

        String packageName = getPackageName();
        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                    && appProcess.processName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    private String extractTitleFromUrl(String url) {
        try {
            // 移除协议部分
            url = url.replaceFirst("https?://", "");
            // 移除参数部分
            url = url.split("\\?")[0];
            // 移除路径，只保留域名部分
            String[] parts = url.split("/");
            if (parts.length > 0) {
                return parts[0];
            }
        } catch (Exception e) {
            Log.e("ExtractTitle", "Error extracting title from URL", e);
        }
        return "新链接";
    }

    private void handleClipboardText(String text) {
        if (TextUtils.isEmpty(text)) {
            return;
        }

        Log.d("LinkParser", "Processing text: " + text);
        
        // 查找第一个 http(s) URL
        Pattern urlPattern = Pattern.compile("https?://[^\\s,，]+");
        Matcher urlMatcher = urlPattern.matcher(text);
        
        if (urlMatcher.find()) {
            // 获取URL
            String url = urlMatcher.group(0);
            
            // 获取URL之前的内容作为标题
            String title = text.substring(0, urlMatcher.start()).trim();
            
            // 如果标题为空或者太长，进行处理
            if (TextUtils.isEmpty(title)) {
                title = "新链接";
            } else if (title.length() > 20) {
                title = title.substring(0, 20) + "...";
            }
            
            Log.d("LinkParser", "Extracted title: " + title);
            Log.d("LinkParser", "Extracted URL: " + url);

            // 显示保存对话框
            final String finalTitle = title;
            final String finalUrl = url;
            new Handler(Looper.getMainLooper()).post(() -> {
                showSaveLinkDialog(finalUrl, finalTitle);
            });
        }
    }

    private void showSaveLinkDialog(String url, String initialTitle) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_save_link, null);
        EditText titleInput = dialogView.findViewById(R.id.edit_title);
        EditText urlInput = dialogView.findViewById(R.id.edit_url);

        // 设置初始值
        titleInput.setText(initialTitle);
        urlInput.setText(url);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("保存链接")
                .setView(dialogView)
                .setPositiveButton("保存", (d, which) -> {
                    String title = titleInput.getText().toString();
                    String finalUrl = urlInput.getText().toString();
                    
                    if (!TextUtils.isEmpty(title) && !TextUtils.isEmpty(finalUrl)) {
                        LinkItem newLink = new LinkItem(
                            title,
                            finalUrl,
                            "clipboard",
                            "clipboard",
                            ""
                        );

                        linkDao.insertLink(newLink);
                        
                        // 清除剪贴板
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""));

                        Snackbar.make(binding.getRoot(), "已保存：" + title, 
                                Snackbar.LENGTH_LONG).show();

                        NavController navController = Navigation.findNavController(this, 
                                R.id.nav_host_fragment_content_main);
                        navController.navigate(R.id.nav_home);
                    }
                })
                .setNegativeButton("取消", (dialog1, which) -> {
                    // 用户取消时也清除剪贴板，避免再次触发
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""));
                })
                .create();

        // 添加对话框关闭监听
        dialog.setOnDismissListener(dialogInterface -> {
            // 对话框关闭时（包括点击外部区域）也清除剪贴板
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""));
        });

        dialog.show();

        // 在后台获取网页标题
        fetchTitleFromUrl(url, titleInput);
    }

    private void fetchTitleFromUrl(String url, EditText titleInput) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            String title = "";
            try {
                URL urlObj = new URL(url);
                URLConnection conn = urlObj.openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setRequestProperty("User-Agent", 
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");

                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)
                );
                
                StringBuilder html = new StringBuilder();
                String line;
                int linesRead = 0;
                while ((line = reader.readLine()) != null && linesRead < 100) {
                    html.append(line);
                    linesRead++;
                }
                reader.close();

                Pattern pattern = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(html.toString());
                if (matcher.find()) {
                    title = matcher.group(1).trim();
                    // 如果标题超过20个字符，截取前20个字符并添加省略号
                    if (title.length() > 20) {
                        title = title.substring(0, 20) + "...";
                    }
                }
            } catch (Exception e) {
                Log.e("FetchTitle", "Error fetching title", e);
            }

            final String finalTitle = title;
            handler.post(() -> {
                if (!TextUtils.isEmpty(finalTitle)) {
                    titleInput.setText(finalTitle);
                }
            });
        });
    }
}