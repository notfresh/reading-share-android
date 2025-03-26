package person.notfresh.readingshare;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
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

import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.navigation.NavigationView;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import person.notfresh.readingshare.databinding.ActivityMainBinding;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.model.LinkItem;
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
import android.content.SharedPreferences;
import androidx.core.view.GravityCompat;
import person.notfresh.readingshare.util.BilibiliUrlConverter;
import person.notfresh.readingshare.util.CrawlUtil;
import person.notfresh.readingshare.util.RecentTagsManager;

import java.io.IOException;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;
    private LinkDao linkDao;
    private boolean hasFocus = false;
    private String lastClipboardText = "";  // 添加这个变量来记录上次处理的剪贴板内容
    private NavController navController;  // 将 navController 声明为类成员变量

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            binding = ActivityMainBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());

            linkDao = new LinkDao(this);
            linkDao.open();

            setSupportActionBar(binding.appBarMain.toolbar);
            
            DrawerLayout drawer = binding.drawerLayout;
            NavigationView navigationView = binding.navView;
            
            // 先设置导航控制器
            navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
            
            // 读取默认Tab设置并导航
            SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
            int defaultTab = prefs.getInt("default_tab", 0); // 默认为0，即首页
            
            // 根据设置选择目标页面ID
            int destinationId;
            switch (defaultTab) {
                case 1: // 标签页
                    destinationId = R.id.nav_tags;
                    break;
                default: // 首页
                    destinationId = R.id.nav_home;
                    break;
            }
            
            // 导航到默认页面
            navController.navigate(destinationId);
            
            // 然后设置 AppBarConfiguration
            mAppBarConfiguration = new AppBarConfiguration.Builder(
                    R.id.nav_home, R.id.nav_tags, R.id.nav_slideshow, R.id.nav_rss, R.id.nav_archive)
                    .setOpenableLayout(drawer)
                    .build();

            NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
            NavigationUI.setupWithNavController(navigationView, navController);

            // 设置导航头部点击事件
            View headerView = navigationView.getHeaderView(0);
            headerView.setOnClickListener(v -> {
                drawer.closeDrawer(GravityCompat.START);
                new Handler().postDelayed(() -> {
                    Intent intent = new Intent(this, UserProfileActivity.class);
                    startActivity(intent);
                }, 250);
            });

            // 处理分享意图
            handleIntent(getIntent());

            // 设置导航项点击事件
            navigationView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();
                drawer.closeDrawer(GravityCompat.START);

                new Handler().postDelayed(() -> {
                    try {
                        if (id == R.id.nav_home) {
                            navController.navigate(R.id.nav_home);
                        } else if (id == R.id.nav_tags) {
                            navController.navigate(R.id.nav_tags);
                        } else if (id == R.id.nav_rss) {
                            navController.navigate(R.id.nav_rss);
                        } else if (id == R.id.nav_slideshow) {
                            navController.navigate(R.id.nav_slideshow);
                        } else if (id == R.id.nav_archive) {
                            navController.navigate(R.id.nav_archive);
                        }
                    } catch (Exception e) {
                        Log.e("MainActivity", "Navigation failed", e);
                        Toast.makeText(this, "导航失败", Toast.LENGTH_SHORT).show();
                    }
                }, 250);

                return true;
            });

            checkClipboardPermission();
            
        } catch (Exception e) {
            Log.e("MainActivity", "onCreate failed", e);
            Toast.makeText(this, "应用启动失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override 
    public void onWindowFocusChanged(boolean hasFocus) { //@mark
        super.onWindowFocusChanged(hasFocus);
        this.hasFocus = hasFocus;
        
        if (hasFocus) {
            // 当窗口真正获得焦点时检查剪贴板
            checkClipboard(); //@Def Line300
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNavHeader();
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
                    if (clipboard == null || !clipboard.hasPrimaryClip()) {
                        return;
                    }
                    ClipData clipData = clipboard.getPrimaryClip();
                    if (clipData == null || clipData.getItemCount() == 0) {
                        return;
                    }
                    CharSequence clipTextSequence = clipData.getItemAt(0).getText();
                    if (clipTextSequence == null) {
                        return;
                    }
                    SharedPreferences prefs = getSharedPreferences("clipboard_prefs", MODE_PRIVATE);
                    String savedText = prefs.getString("last_clipboard_text", "");
                    if (!clipTextSequence.toString().equals(savedText)) {
                        lastClipboardText = clipTextSequence.toString();
                        handleClipboardText(lastClipboardText); //@Def Line376
                    }
                    prefs.edit().putString("last_clipboard_text", clipTextSequence.toString()).apply();

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
            // 移除URL中可能的查询参数
            String cleanUrl = url.split("\\?")[0];
            // 检查是否以.xml结尾（忽略大小写）
            if (cleanUrl.toLowerCase().endsWith(".xml")) {
                return;
            }
            
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
        EditText tagsInput = dialogView.findViewById(R.id.edit_tags);
        FlexboxLayout recentTagsContainer = dialogView.findViewById(R.id.recent_tags_container);

        // 设置初始值和提示
        titleInput.setHint(initialTitle);  // 使用 hint 显示灰色提示文字
        urlInput.setText(url);

        // 让标题输入框获得焦点
        titleInput.requestFocus();

        // 获取并显示最近标签
        Context context = this;
        List<String> recentTags = RecentTagsManager.getRecentTags(context);
        if (!recentTags.isEmpty()) {
            TextView recentTagsLabel = dialogView.findViewById(R.id.text_recent_tags_label);
            recentTagsLabel.setVisibility(View.VISIBLE);

            for (String tag : recentTags) {
                TextView tagView = (TextView) LayoutInflater.from(context)
                        .inflate(R.layout.item_recent_tag, recentTagsContainer, false);
                tagView.setText(tag);
                tagView.setOnClickListener(v -> {
                    String currentText = tagsInput.getText().toString().trim();
                    // 如果输入框不为空且不以逗号结尾，添加逗号分隔符
                    if (!currentText.isEmpty() && !currentText.endsWith(",") && !currentText.endsWith("，")) {
                        tagsInput.setText(currentText + "，" + tag);
                    } else {
                        // 如果为空或以逗号结尾，直接添加标签
                        tagsInput.setText(currentText + tag);
                    }
                    // 将光标移到末尾
                    tagsInput.setSelection(tagsInput.getText().length());
                });
                recentTagsContainer.addView(tagView);
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("保存链接")
                .setView(dialogView)
                .setPositiveButton("保存", (d, which) -> {
                    // 如果用户没有输入，使用 hint 中的默认值
                    String title = titleInput.getText().toString();
                    if (TextUtils.isEmpty(title)) {
                        title = initialTitle;
                    }
                    String finalUrl = urlInput.getText().toString();
                    
                    if (!TextUtils.isEmpty(title) && !TextUtils.isEmpty(finalUrl)) {
                        LinkItem newLink = new LinkItem(
                            title,
                            finalUrl,
                            "clipboard",
                            "clipboard",
                            ""
                        );
                        String[] tags = tagsInput.getText().toString().split("[,，]");
                        List<String> tagList = new ArrayList<>();
                        for (String tag : tags) {
                            String trimmedTag = tag.trim();
                            if (!trimmedTag.isEmpty()) {
                                tagList.add(trimmedTag);
                            }
                        }
                        newLink.setTags(tagList);
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
                    // ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    // clipboard.setPrimaryClip(ClipData.newPlainText("", ""));
                })
                .create();

        // 添加对话框关闭监听
        dialog.setOnDismissListener(dialogInterface -> {
            // 对话框关闭时（包括点击外部区域）也清除剪贴板
            // ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            // clipboard.setPrimaryClip(ClipData.newPlainText("", ""));
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
                if (url.contains("weixin.qq.com")) {
                    title = fetchTitleFromWeixin(url, titleInput);
                } else {
                    title = fetchTitleCommon(url, titleInput);
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
    private String fetchTitleFromWeixin(String url, EditText titleInput) throws IOException {
        String title = CrawlUtil.getWeixinArticleTitle(url);
        return title;
    }

    private String fetchTitleCommon(String url, EditText titleInput) throws IOException {
        String title = "";
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
        return title;
    }

    @SuppressLint("ResourceType")
    private void updateNavHeader() {
        try {
            NavigationView navigationView = binding.navView;
            View headerView = navigationView.getHeaderView(0);
            ImageView profileImage = headerView.findViewById(R.id.nav_header_image);
            TextView usernameText = headerView.findViewById(R.id.nav_header_username);
            TextView emailText = headerView.findViewById(R.id.nav_header_email);

            SharedPreferences prefs = getSharedPreferences("UserProfile", MODE_PRIVATE);
            String username = prefs.getString("username", getString(R.string.nav_header_title));
            String email = prefs.getString("email", getString(R.string.nav_header_subtitle));
            String imageUri = prefs.getString("profile_image", "");

            usernameText.setText(username);
            emailText.setText(email);

            // 检查是否有自定义头像
            if (!TextUtils.isEmpty(imageUri)) {
                try {
                    Uri uri = Uri.parse(imageUri);
                    if (uri.getPath() != null && new File(uri.getPath()).exists()) {
                        profileImage.setImageURI(null); // 清除之前的图片
                        profileImage.setImageURI(uri);
                    } else {
                        // 如果自定义头像文件不存在，清除URI并使用默认logo
                        prefs.edit().remove("profile_image").apply();
                        profileImage.setImageResource(R.mipmap.ic_launcher);
                    }
                } catch (Exception e) {
                    Log.e("MainActivity", "Error loading profile image", e);
                    profileImage.setImageResource(R.mipmap.ic_launcher);
                }
            }
            // 如果没有设置自定义头像，保持使用默认的ic_launcher（在XML中已设置）
        } catch (Exception e) {
            Log.e("MainActivity", "Error updating nav header", e);
        }
    }

}