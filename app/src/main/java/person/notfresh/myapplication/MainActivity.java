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

public class MainActivity extends AppCompatActivity implements LinksAdapter.OnLinkActionListener {

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;
    private LinkDao linkDao;
    private LinksAdapter adapter;

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

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        adapter = new LinksAdapter(this);
        adapter.setOnLinkActionListener(this);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // 加载按日期分组的链接
        Map<String, List<LinkItem>> groupedLinks = linkDao.getLinksGroupByDate();
        adapter.setGroupedLinks(groupedLinks);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
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
    public void onDeleteLink(LinkItem link) {
        linkDao.deleteLink(link.getUrl());
    }

    @Override
    public void onUpdateLink(LinkItem oldLink, String newTitle) {
        linkDao.updateLinkTitle(oldLink.getUrl(), newTitle);
    }

    @Override
    public void addTagToLink(LinkItem item, String tag) {
        // 处理添加标签
        linkDao.addTagToLink(item.getId(), tag);
        // 刷新链接列表
        Map<String, List<LinkItem>> groupedLinks = linkDao.getLinksGroupByDate();
        adapter.setGroupedLinks(groupedLinks);
    }

    @Override
    public void updateLinkTags(LinkItem item) {
        // 更新链接的标签
        linkDao.updateLinkTags(item);
        // 刷新链接列表
        Map<String, List<LinkItem>> groupedLinks = linkDao.getLinksGroupByDate();
        adapter.setGroupedLinks(groupedLinks);
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
}