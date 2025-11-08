package person.notfresh.readingshare.ui.tag;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexWrap;
import com.google.android.material.snackbar.Snackbar;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.ImageView;
import android.text.InputType;
import android.widget.PopupMenu;
import android.graphics.Typeface;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.adapter.LinksAdapter;
import person.notfresh.readingshare.adapter.TagsAdapter;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.util.ExportUtil;
import person.notfresh.readingshare.util.ShareUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class TagsFragment extends Fragment implements LinksAdapter.OnLinkActionListener {
    private static final String PREF_NAME = "TagsPreferences";
    private static final String KEY_LAST_TAG = "lastSelectedTag";
    private static final String KEY_SELECTED_TAGS = "selectedTags";
    private static final String KEY_NO_TAG_SELECTED = "noTagSelected";
    private static final String NO_TAG = "NO_TAG";  // 用于表示"无标签"选项
    private static final String TAG_VIEW_NO_TAG = "NO_TAG_VIEW";
    private static final String PREF_HIGHLIGHTED_TAGS = "highlighted_tags";
    
    private RecyclerView tagsRecyclerView;
    private TagsAdapter tagsAdapter;
    private ItemTouchHelper itemTouchHelper;
    private RecyclerView linksRecyclerView;
    private LinksAdapter linksAdapter;
    private LinkDao linkDao;
    private Set<String> selectedTagNames = new HashSet<>();  // 使用Set存储选中的标签名称
    private MenuItem shareMenuItem;
    private MenuItem closeSelectionMenuItem;
    private MenuItem selectAllMenuItem;  // 添加全选菜单项引用
    private MenuItem sortMenuItem;  // 排序按钮
    private MenuItem exitSortMenuItem;  // 退出排序按钮
    private boolean isSelectionMode = false;
    private boolean isSortMode = false;  // 排序模式状态
    private ScrollView tagsScrollView;
    private View toggleTagsButton;
    private ImageView arrowIndicator;
    private boolean isTagsExpanded = false;  // 默认折叠
    private static final int COLLAPSED_HEIGHT_DP = 120;  // 减小折叠高度
    private Set<String> highlightedTags = new HashSet<>();  // 保存高亮的标签

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);  // 启用选项菜单
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                            Bundle savedInstanceState) {
        Log.d("TagsFragment", "onCreateView started");
        View root = inflater.inflate(R.layout.fragment_tags, container, false);

        // 初始化标签 RecyclerView
        tagsRecyclerView = root.findViewById(R.id.recycler_tags);
        FlexboxLayoutManager layoutManager = new FlexboxLayoutManager(requireContext());
        layoutManager.setFlexDirection(FlexDirection.ROW);
        layoutManager.setFlexWrap(FlexWrap.WRAP);
        tagsRecyclerView.setLayoutManager(layoutManager);
        
        tagsAdapter = new TagsAdapter();
        tagsAdapter.setOnTagClickListener((position, tag) -> {
            if (!isSortMode) {
                handleTagClick(tag.getName());
            }
        });
        tagsAdapter.setOnTagLongClickListener((position, tag) -> {
            if (!isSortMode) {
                showTagOptionsDialog(tag.getName());
            }
        });
        tagsRecyclerView.setAdapter(tagsAdapter);
        
        // 初始化 ItemTouchHelper（但先不附加，等排序模式开启时再附加）
        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP | ItemTouchHelper.DOWN | 
            ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT,
            0
        ) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, 
                                 @NonNull RecyclerView.ViewHolder viewHolder, 
                                 @NonNull RecyclerView.ViewHolder target) {
                int fromPos = viewHolder.getAdapterPosition();
                int toPos = target.getAdapterPosition();
                tagsAdapter.swapItems(fromPos, toPos);
                
                // 保存排序到数据库
                List<Long> orderedTagIds = new ArrayList<>();
                for (TagsAdapter.TagItem tag : tagsAdapter.getTags()) {
                    orderedTagIds.add(tag.getId());
                }
                linkDao.saveTagOrder(orderedTagIds);
                
                return true;
            }
            
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // 不需要实现
            }
            
            @Override
            public boolean isLongPressDragEnabled() {
                return isSortMode;  // 只在排序模式下启用
            }
        };
        itemTouchHelper = new ItemTouchHelper(callback);
        
        // 获取标签容器和展开/折叠相关视图
        tagsScrollView = root.findViewById(R.id.tags_scrollview);
        toggleTagsButton = root.findViewById(R.id.btn_toggle_tags);
        arrowIndicator = root.findViewById(R.id.arrow_indicator);

        // 设置初始高度为 wrap_content
        ViewGroup.LayoutParams params = tagsScrollView.getLayoutParams();
        if(!isTagsExpanded){
            // 设置为屏幕高度的25%
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            params.height = screenHeight / 4;
        }else{
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        tagsScrollView.setLayoutParams(params);
        
        // 设置展开/折叠按钮点击事件
        toggleTagsButton.setOnClickListener(v -> toggleTagsExpansion());
        
        // 设置初始箭头状态为向上(收起)
        arrowIndicator.setImageResource(R.drawable.ic_expand_less);
        arrowIndicator.setContentDescription("收起标签");
        
        linkDao = new LinkDao(requireContext());
        linkDao.open();

        // 设置 RecyclerView
        linksAdapter = new LinksAdapter(requireContext());
        linksAdapter.setOnLinkActionListener(this);
        linksRecyclerView = root.findViewById(R.id.recycler_links);
        linksRecyclerView.setAdapter(linksAdapter);
        linksRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        // 在标签页默认不显示置顶区（仅在与筛选结果有交集时显示）
        linksAdapter.setPinnedLinks(Collections.emptyList());

        // 启用滑动操作功能
        linksAdapter.enableSwipeActions(linksRecyclerView);

        // 加载高亮标签设置
        loadHighlightedTags();
        
        // 初始加载所有内容
        List<LinkItem> allLinks = linkDao.getAllLinks();
        linksAdapter.setLinks(allLinks);

        // 加载所有标签
        Log.d("TagsFragment", "About to load tags");
        loadTags();
        restoreSelections();
        Log.d("TagsFragment", "Tags loaded");

        return root;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.tags_menu, menu);
        shareMenuItem = menu.findItem(R.id.action_share);
        closeSelectionMenuItem = menu.findItem(R.id.action_close_selection);
        selectAllMenuItem = menu.findItem(R.id.action_select_all);  // 获取全选菜单项
        sortMenuItem = menu.findItem(R.id.action_sort_tags);  // 获取排序按钮
        exitSortMenuItem = menu.findItem(R.id.action_exit_sort);  // 获取退出排序按钮
        
        // 设置菜单项可见性
        shareMenuItem.setVisible(isSelectionMode);
        closeSelectionMenuItem.setVisible(isSelectionMode);
        selectAllMenuItem.setVisible(isSelectionMode);
        sortMenuItem.setVisible(!isSortMode && !isSelectionMode);  // 非排序模式且非选择模式时显示
        exitSortMenuItem.setVisible(isSortMode);  // 排序模式时显示
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_add_tag) {
            showAddTagDialog();
            return true;
        } else if (id == R.id.action_sort_tags) {
            toggleSortMode();
            return true;
        } else if (id == R.id.action_exit_sort) {
            toggleSortMode();
            return true;
        } else if (id == R.id.action_close_selection) {
            toggleSelectionMode();
            return true;
        } else if (id == R.id.action_select_all) {
            selectAllItems();  // 处理全选
            return true;
        } else if (id == R.id.action_share_text) {
            shareAsText();
            return true;
        } else if (id == R.id.action_share_json) {
            shareAsFile(true);  // true 表示 JSON
            return true;
        } else if (id == R.id.action_share_csv) {
            shareAsFile(false);  // false 表示 CSV
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (linkDao != null) {
            linkDao.close();
        }
    }

    private void showAddTagDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_tag, null);
        EditText tagInput = dialogView.findViewById(R.id.edit_tag_input);

        new AlertDialog.Builder(requireContext())
                .setTitle("添加新标签")
                .setView(dialogView)
                .setPositiveButton("添加", (dialog, which) -> {
                    String tagName = tagInput.getText().toString().trim();
                    if (!TextUtils.isEmpty(tagName)) {
                        // 检查标签是否已存在
                        List<String> existingTags = linkDao.getAllTags();
                        if (existingTags.contains(tagName)) {
                            // 如果标签已存在，显示提示
                            Snackbar.make(requireView(), "标签已存在", Snackbar.LENGTH_SHORT).show();
                            return;
                        }
                        
                        // 添加新标签
                        long tagId = linkDao.addTag(tagName);
                        if (tagId != -1) {
                            // 重新加载标签列表
                            loadTags();
                            // 显示提示
                            Snackbar.make(requireView(), "已添加标签：" + tagName, Snackbar.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void loadTags() {
        if (tagsAdapter == null) {
            Log.e("TagsFragment", "tagsAdapter is null in loadTags()");
            return;
        }
        
        // 使用后台线程加载标签数据
        new Thread(() -> {
            // 后台获取标签数据
            Map<String, Integer> tagsWithCount = linkDao.getTagsWithCount();
            Log.d("TagsFragment", "Tags loaded: " + tagsWithCount.size());
            
            // 获取无标签的链接数量
            int noTagCount = linkDao.getLinksWithoutTags().size();
            
            // 回到主线程更新UI
            requireActivity().runOnUiThread(() -> {
                List<TagsAdapter.TagItem> tagItems = new ArrayList<>();
                
                // 添加"无标签"选项（如果数量大于0）
                if (noTagCount > 0) {
                    tagItems.add(new TagsAdapter.TagItem(-1, NO_TAG, noTagCount));
                }
                
                // 添加其他标签
                for (Map.Entry<String, Integer> entry : tagsWithCount.entrySet()) {
                    long tagId = linkDao.getTagIdByName(entry.getKey());
                    if (tagId != -1) {
                        tagItems.add(new TagsAdapter.TagItem(tagId, entry.getKey(), entry.getValue()));
                    }
                }
                
                tagsAdapter.setTags(tagItems);
                tagsAdapter.setHighlightedTags(highlightedTags);
                tagsAdapter.setSelectedTagNames(selectedTagNames);
                
                // 标签加载完成后，检查是否需要显示展开按钮
                tagsRecyclerView.post(() -> {
                    int height = tagsRecyclerView.getHeight();
                    float density = getResources().getDisplayMetrics().density;
                    int collapsedHeightPx = (int) (COLLAPSED_HEIGHT_DP * density);
                    toggleTagsButton.setVisibility(height > collapsedHeightPx ? View.VISIBLE : View.GONE);
                });
                // 恢复选择状态
                restoreSelections();
            });
        }).start();
    }
    
    private void handleTagClick(String tagName) {
        if (NO_TAG.equals(tagName)) {
            Log.d("TagsFragment", "Clicked no tags");
            updateTagSelectionByName(tagName);
        } else {
            updateTagSelectionByName(tagName);
        }
    }
    
    private void updateTagSelectionByName(String tagName) {
        if (selectedTagNames.contains(tagName)) {
            // 取消选择
            selectedTagNames.remove(tagName);
            
            if (selectedTagNames.isEmpty()) {
                requireActivity().setTitle("全部内容");
                List<LinkItem> allLinks = linkDao.getAllLinks();
                linksAdapter.setLinks(allLinks);
                clearSavedSelections();
            } else {
                // 根据剩余选中的标签筛选内容
                updateContentBySelectedTags();
            }
        } else {
            // 选中新标签
            selectedTagNames.add(tagName);
            updateContentBySelectedTags();
        }
        
        // 更新适配器显示
        if (tagsAdapter != null) {
            tagsAdapter.setSelectedTagNames(selectedTagNames);
        }
    }

    // 已废弃：使用 TagsAdapter 替代
    // private void addTagView(String tag, int count, boolean isSelected) { ... }

    // 已废弃：样式由 TagsAdapter 管理
    // private void setTagStyle(View tagView, boolean isSelected) { ... }
    
    // 已废弃：使用 updateTagSelectionByName 替代
    // private void updateTagSelection(View tagView) { ... }

    // 已废弃：使用新的 handleTagClick(String tagName) 替代

    private void showTagOptionsDialog(String tag) {
        String[] options = {"删除标签",  "删除标签及所有关联链接","发布到网站","切换高亮"};
        
        new AlertDialog.Builder(requireContext())
            .setTitle("标签操作")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: // 删除标签
                        confirmDeleteTag(tag, false);
                        break;
                    case 1: // 删除标签及所有关联链接
                        confirmDeleteTag(tag, true);
                        break;
                    case 2: // 发布到网站
                        publishTagToWebsite(tag);
                        break;
                    case 3: // 高亮标签
                        toggleTagHighlight(tag);
                        break;
                }
            })
            .show();
    }

    private void confirmDeleteTag(String tag, boolean isCascading) {
        try {
            new AlertDialog.Builder(requireContext())
                .setTitle("确认删除")
                .setMessage("确定要删除标签 \"" + tag + "\" 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    try {
                        Log.d("TagsFragment", "开始删除标签: " + tag);
                        
                        // 从数据库中删除标签
                        if(!isCascading){
                            linkDao.deleteTag(tag);
                        }else{ // 级联删除
                            linkDao.deleteTagWithLinks(tag);
                        }
                        Log.d("TagsFragment", "标签已从数据库删除");
                        
                        // 从当前选中的标签集合中移除
                        selectedTagNames.remove(tag);
                        Log.d("TagsFragment", "标签已从选中集合移除");
                        
                        // 重新加载标签
                        loadTags();
                        Log.d("TagsFragment", "标签列表已重新加载");
                        
                        // 更新链接列表
                        List<LinkItem> allLinks = linkDao.getAllLinks();
                        linksAdapter.setLinks(allLinks);
                        linksAdapter.notifyDataSetChanged();
                        Log.d("TagsFragment", "链接列表已更新");
                        
                        // 显示成功提示
                        Toast.makeText(requireContext(), 
                            "标签已删除", 
                            Toast.LENGTH_SHORT).show();
                        
                    } catch (Exception e) {
                        Log.e("TagsFragment", "删除标签时出错", e);
                        Toast.makeText(requireContext(), 
                            "删除标签失败: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
        } catch (Exception e) {
            Log.e("TagsFragment", "显示确认对话框时出错", e);
            Toast.makeText(requireContext(), 
                "操作失败: " + e.getMessage(), 
                Toast.LENGTH_SHORT).show();
        }

        // 可以在此处添加高度检测 (如果需要)
        if (tagsRecyclerView != null) {
            tagsRecyclerView.post(this::checkTagsVisibility);
        }
    }

    private void publishTagToWebsite(String tag) {
        Log.d("TagsFragment", "开始发布标签到网站: " + tag);
        
        // 获取该标签下的所有链接
        List<LinkItem> links = linkDao.getLinksByTag(tag);
        Log.d("TagsFragment", "获取到标签相关链接数量: " + links.size());
        
        SharedPreferences prefs = requireActivity().getSharedPreferences("UserProfile", Context.MODE_PRIVATE);
        String username = prefs.getString("username", "anonymous"); // 如果没有设置用户名，使用 "anonymous"
        

        // 创建 JSON 数据
        try {
            JSONObject jsonData = new JSONObject();
            jsonData.put("tag", tag);
            
            JSONArray linksArray = new JSONArray();
            for (LinkItem link : links) {
                JSONObject linkObj = new JSONObject();
                linkObj.put("title", link.getTitle());
                linkObj.put("url", link.getUrl());
                SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                linkObj.put("timestamp", isoFormat.format(new Date(link.getTimestamp())));
                linksArray.put(linkObj);
            }
            Log.d("TagsFragment", "linksArray Item0  " + linksArray.get(0).toString());
            jsonData.put("links", linksArray);
            jsonData.put("username", username);
            
            Log.d("TagsFragment", "构建的 JSON 数据: " + jsonData.toString());

            // 在后台线程执行网络请求
            new Thread(() -> {
                try {
                    URL url = new URL("https://duxiang.ai/api/publishTaggedLinks");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    
                    // 添加信任所有证书的配置
                    if (conn instanceof javax.net.ssl.HttpsURLConnection) {
                        javax.net.ssl.HttpsURLConnection httpsConn = (javax.net.ssl.HttpsURLConnection) conn;
                        javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[]{
                            new javax.net.ssl.X509TrustManager() {
                                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                                    return null;
                                }
                                public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                                }
                                public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                                }
                            }
                        };

                        javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("SSL");
                        sc.init(null, trustAllCerts, new java.security.SecureRandom());
                        httpsConn.setSSLSocketFactory(sc.getSocketFactory());
                        httpsConn.setHostnameVerifier((hostname, session) -> true);
                    }

                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    
                    Log.d("TagsFragment", "开始发送数据...");

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonData.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                    int responseCode = conn.getResponseCode();
                    Log.d("TagsFragment", "服务器响应码: " + responseCode);

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        try {
                            // 读取服务器返回的数据
                            BufferedReader reader = new BufferedReader(
                                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                            StringBuilder response = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                response.append(line);
                            }
                            reader.close();

                            // 解析返回的数据
                            JSONObject jsonResponse = new JSONObject(response.toString());
                            String publishUrl = jsonResponse.optString("url");
                            if (publishUrl.isEmpty()) {
                                throw new JSONException("服务器返回的URL为空");
                            }
                            
                            // 保存发布记录到 SharedPreferences
                            SharedPreferences.Editor editor = prefs.edit();
                            String publishedTagsStr = prefs.getString("published_tags", "[]");
                            JSONArray publishedTags;
                            try {
                                publishedTags = new JSONArray(publishedTagsStr);
                            } catch (JSONException e) {
                                publishedTags = new JSONArray();
                            }
                            
                            // 检查是否已经发布过这个标签
                            boolean isTagExists = false;
                            for (int i = 0; i < publishedTags.length(); i++) {
                                JSONObject existingTag = publishedTags.optJSONObject(i);
                                if (existingTag != null && tag.equals(existingTag.optString("tag"))) {
                                    existingTag.put("url", publishUrl);
                                    existingTag.put("timestamp", System.currentTimeMillis());
                                    isTagExists = true;
                                    break;
                                }
                            }
                            
                            // 如果是新标签，添加到数组中
                            if (!isTagExists) {
                                JSONObject publishRecord = new JSONObject();
                                publishRecord.put("tag", tag);
                                publishRecord.put("url", publishUrl);
                                publishRecord.put("timestamp", System.currentTimeMillis());
                                publishedTags.put(publishRecord);
                            }
                            
                            editor.putString("published_tags", publishedTags.toString());
                            editor.apply();

                            Activity activity = getActivity();
                            if (activity != null && !activity.isFinishing()) {
                                activity.runOnUiThread(() -> {
                                    try {
                                        // 显示成功消息和访问链接
                                        View view = getView();
                                        if (view != null) {
                                            Snackbar snackbar = Snackbar.make(view, "发布成功", Snackbar.LENGTH_LONG)
                                                .setAction("访问", v -> {
                                                    try {
                                                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(publishUrl));
                                                        startActivity(intent);
                                                    } catch (Exception e) {
                                                        Toast.makeText(requireContext(), "无法打开链接", Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                            snackbar.show();
                                        }
                                        
                                        // 通知 UserProfileActivity 更新
                                        Context context = getContext();
                                        if (context != null) {
                                            Intent updateIntent = new Intent("UPDATE_PUBLISHED_TAGS");
                                            context.sendBroadcast(updateIntent);
                                        }
                                    } catch (Exception e) {
                                        Log.e("TagsFragment", "UI更新失败", e);
                                    }
                                });
                            }
                        } catch (Exception e) {
                            Log.e("TagsFragment", "处理服务器响应失败", e);
                            throw e;
                        }
                    } else {
                        throw new IOException("服务器返回错误: " + responseCode);
                    }

                } catch (Exception e) {
                    Log.e("TagsFragment", "发布失败", e);
                    getActivity().runOnUiThread(() -> {
                        Snackbar.make(requireView(), "发布失败: " + e.getMessage(), 
                            Snackbar.LENGTH_LONG).show();
                    });
                }
            }).start();

        } catch (JSONException e) {
            Log.e("TagsFragment", "JSON 构建失败", e);
            Snackbar.make(requireView(), "准备数据失败: " + e.getMessage(), 
                Snackbar.LENGTH_LONG).show();
        }
    }

    private void updateContentBySelectedTags() {
        List<LinkItem> links = new ArrayList<>();
        Set<String> tagNames = new HashSet<>();
        boolean hasNoTagFilter = false;

        // 收集选中的标签名称
        for (String tagName : selectedTagNames) {
            if (NO_TAG.equals(tagName)) {
                hasNoTagFilter = true;
            } else {
                tagNames.add(tagName);
            }
        }

        // 根据选择获取链接
        if (selectedTagNames.isEmpty()) {
            // 如果没有选中任何标签，显示所有链接
            links = linkDao.getAllLinks();
        } else {
            // 如果选中了"无标签"
            if (hasNoTagFilter) {
                links.addAll(linkDao.getLinksWithoutTags());
            }
            // 如果还选中了其他标签
            if (!tagNames.isEmpty()) {
                links.addAll(linkDao.getLinksByTags(tagNames));
            }
        }

        // 更新标题
        updateTitle(tagNames, hasNoTagFilter);

        // 按日期分组显示
        Map<String, List<LinkItem>> groupedLinks = new TreeMap<>(Collections.reverseOrder());
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        
        // 对链接列表按时间戳排序
        Collections.sort(links, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        
        for (LinkItem link : links) {
            String date = dateFormat.format(new Date(link.getTimestamp()));
            List<LinkItem> dayLinks = groupedLinks.computeIfAbsent(date, k -> new ArrayList<>());
            dayLinks.add(link);
        }
        
        // 确保每个日期组内的链接也按时间排序
        for (List<LinkItem> dayLinks : groupedLinks.values()) {
            Collections.sort(dayLinks, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        }
        
        // 计算置顶与筛选结果的交集，仅当有交集时展示
        if (selectedTagNames.isEmpty()) {
            // 未选择任何标签时不显示置顶区
            linksAdapter.setPinnedLinks(Collections.emptyList());
        } else {
            // 使用链接 id 作为交集判断依据
            Set<Long> selectedIds = new HashSet<>();
            for (LinkItem item : links) {
                selectedIds.add(item.getId());
            }
            List<LinkItem> pinned = linkDao.getPinnedLinks();
            List<LinkItem> pinnedOverlap = new ArrayList<>();
            for (LinkItem p : pinned) {
                if (selectedIds.contains(p.getId())) {
                    pinnedOverlap.add(p);
                }
            }
            linksAdapter.setPinnedLinks(pinnedOverlap);
        }

        linksAdapter.setGroupedLinks(groupedLinks);

        // 保存选择状态
        saveSelections(tagNames, hasNoTagFilter);
    }

    private void updateTitle(Set<String> tags, boolean includeNoTag) {
        StringBuilder title = new StringBuilder();
        if (tags.isEmpty() && !includeNoTag) {
            title.append("全部内容");
        } else {
            if (includeNoTag) {
                title.append("无标签");
                if (!tags.isEmpty()) {
                    title.append(" + ");
                }
            }
            if (!tags.isEmpty()) {
                title.append(String.join(" + ", tags));
            }
        }
        requireActivity().setTitle(title.toString());
    }

    private void saveSelections(Set<String> tags, boolean includeNoTag) {
        SharedPreferences.Editor editor = requireContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit();
        
        // 保存标签选择
        editor.putStringSet(KEY_SELECTED_TAGS, tags);
        editor.putBoolean(KEY_NO_TAG_SELECTED, includeNoTag);
        editor.apply();
    }

    private void clearSavedSelections() {
        requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }

    private void restoreSelections() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> savedTags = prefs.getStringSet(KEY_SELECTED_TAGS, new HashSet<>());
        boolean noTagSelected = prefs.getBoolean(KEY_NO_TAG_SELECTED, false);

        // 恢复选择状态
        selectedTagNames.clear();
        if (noTagSelected) {
            selectedTagNames.add(NO_TAG);
        }
        selectedTagNames.addAll(savedTags);
        
        // 更新内容显示
        if (!selectedTagNames.isEmpty()) {
            updateContentBySelectedTags();
        }
        
        // 刷新适配器
        if (tagsAdapter != null) {
            tagsAdapter.notifyDataSetChanged();
        }
    }


    // 实现 OnLinkActionListener 的方法
    @Override
    public void onDeleteLink(LinkItem link) {
        linkDao.deleteLink(link.getId());
        // 刷新列表
        loadTags(); // 使用已有的 loadTags() 方法重新加载标签和链接
        restoreSelections();
        linksAdapter.notifyDataSetChanged();
    }
    
    @Override
    public boolean deleteLink(Long id) {
        return false;
    }

    @Override
    public void onUpdateLink(LinkItem oldLink, String newTitle) {
        linkDao.updateLinkTitle(oldLink.getUrl(), newTitle);
        // 重新加载当前标签的链接
        loadTags();
    }


    public void addTagsToLink(LinkItem item, List<String> tags){

    }

    @Override
    public void addTagToLink(LinkItem item, String tag) { //@mark
        // 1. 检查标签是否存在
        List<String> existingTags = linkDao.getAllTags();
        boolean isNewTag = !existingTags.contains(tag);
        
        // 2. 如果是新标签，先创建标签
        if (isNewTag) {
            long tagId = linkDao.addTag(tag);
            if (tagId != -1) {
                // 显示提示
                Snackbar.make(requireView(), "已创建新标签：" + tag, Snackbar.LENGTH_SHORT).show();
                // 重新加载标签列表
                loadTags();
            }
        }
        
        // 3. 为链接添加标签
        //linkDao.addTagToLink(item.getId(), tag);
        
        // 4. 显示提示
        Snackbar.make(requireView(), 
            String.format("已将 %s 添加到标签：%s", item.getTitle(), tag), 
            Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void updateLinkTags(LinkItem item) {
        linkDao.updateLinkTags(item);
        // 重新加载标签和链接
        loadTags();
        // 更新当前显示的内容
        updateContentBySelectedTags();
    }

    @Override
    public void onEnterSelectionMode() {
        if (!isSelectionMode) {
            toggleSelectionMode();
        }
    }

    @Override
    public void onPinStatusChanged() {
        // 置顶状态变化后，依据当前筛选重新计算交集并刷新
        updateContentBySelectedTags();
    }

    private void toggleSelectionMode() {
        isSelectionMode = !isSelectionMode;
        linksAdapter.toggleSelectionMode();
        if (shareMenuItem != null) {
            shareMenuItem.setVisible(isSelectionMode);
        }
        if (closeSelectionMenuItem != null) {
            closeSelectionMenuItem.setVisible(isSelectionMode);
        }
        if (selectAllMenuItem != null) {
            selectAllMenuItem.setVisible(isSelectionMode);
        }
        // 更新标题
        if (isSelectionMode) {
            requireActivity().setTitle("选择要分享的链接");
        } else {
            requireActivity().setTitle("标签");
        }
        requireActivity().invalidateOptionsMenu();
    }

    private void toggleSortMode() {
        isSortMode = !isSortMode;
        
        // 更新菜单项可见性
        if (sortMenuItem != null) {
            sortMenuItem.setVisible(!isSortMode && !isSelectionMode);
        }
        if (exitSortMenuItem != null) {
            exitSortMenuItem.setVisible(isSortMode);
        }
        
        // 启用/禁用拖拽功能
        if (itemTouchHelper != null) {
            if (isSortMode) {
                itemTouchHelper.attachToRecyclerView(tagsRecyclerView);
            } else {
                itemTouchHelper.attachToRecyclerView(null);
            }
        }
        
        // 更新标题
        if (isSortMode) {
            requireActivity().setTitle("拖拽排序标签");
            Toast.makeText(requireContext(), "长按标签可拖拽排序", Toast.LENGTH_SHORT).show();
        } else {
            requireActivity().setTitle("标签");
        }
        
        requireActivity().invalidateOptionsMenu();
    }

    private void shareAsText() {
        Set<LinkItem> selectedItems = linksAdapter.getSelectedItems();
        ShareUtil.shareLinksAsText(requireContext(), new ArrayList<>(selectedItems));
    }

    private void shareAsFile(boolean isJson) {
        Set<LinkItem> selectedItems = linksAdapter.getSelectedItems();
        ShareUtil.shareLinksAsFileWithDialog(requireContext(), new ArrayList<>(selectedItems), isJson);
    }


    private void selectAllItems() {
        // 获取当前标签过滤后的链接
        List<LinkItem> links;
        if (selectedTagNames.isEmpty()) {
            links = linkDao.getAllLinks();  // 如果没有选中标签，获取所有链接
        } else {
            // 收集选中的标签名称
            Set<String> tagNames = new HashSet<>();
            boolean hasNoTagFilter = false;
            
            for (String tagName : selectedTagNames) {
                if (NO_TAG.equals(tagName)) {
                    hasNoTagFilter = true;
                } else {
                    tagNames.add(tagName);
                }
            }
            
            // 根据选中的标签获取链接
            links = new ArrayList<>();
            if (hasNoTagFilter) {
                links.addAll(linkDao.getLinksWithoutTags());
            }
            if (!tagNames.isEmpty()) {
                links.addAll(linkDao.getLinksByTags(tagNames));
            }
        }

        // 选中所有符合条件的链接
        for (LinkItem item : links) {
            linksAdapter.selectItem(item);
        }
        linksAdapter.notifyDataSetChanged();
    }

    private void updateLinksList() {
        // 根据当前选中的标签更新链接列表
        Set<String> tagNames = new HashSet<>();
        boolean hasNoTagFilter = false;
        // 收集选中的标签名称
        for (String tagName : selectedTagNames) {
            if (NO_TAG.equals(tagName)) {
                hasNoTagFilter = true;
            } else {
                tagNames.add(tagName);
            }
        }
        List<LinkItem> links = new ArrayList<>();
        
        if (selectedTagNames.isEmpty()) {
            // 如果没有选中任何标签，显示所有链接
            links = linkDao.getAllLinks();
        } else {
            // 如果选中了"无标签"选项
            if (hasNoTagFilter) {
                links.addAll(linkDao.getLinksWithoutTags());
            }
            // 如果有选中的标签
            if (!tagNames.isEmpty()) {
                links.addAll(linkDao.getLinksByTags(tagNames));
            }
        }

        // 更新适配器数据
        linksAdapter.setLinks(links);
        linksAdapter.notifyDataSetChanged();
    }

    // 已废弃：不再需要，TagsAdapter 直接使用 TagItem 对象
    // private void setTagViewId(View tagView, String tagName) { ... }
    // private String getTagNameFromView(View tagView) { ... }

    private boolean isNoTagView(View tagView) {
        return TAG_VIEW_NO_TAG.equals(tagView.getTag());
    }

    // 更新展开/折叠方法
    private void toggleTagsExpansion() {
        isTagsExpanded = !isTagsExpanded;
        
        ViewGroup.LayoutParams params = tagsScrollView.getLayoutParams();
        
        if (isTagsExpanded) {
            // 展开状态 - 设置为自适应高度，但最大不超过屏幕高度的1/3
            // int maxHeight = getResources().getDisplayMetrics().heightPixels / 3;
            // tagsScrollView.setMaxHeight(maxHeight);
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            arrowIndicator.setImageResource(R.drawable.ic_expand_less);
            arrowIndicator.setContentDescription("收起标签");
        } else {
            // 折叠状态 - 使用固定高度
            float density = getResources().getDisplayMetrics().density;
            params.height = (int) (COLLAPSED_HEIGHT_DP * density);
            arrowIndicator.setImageResource(R.drawable.ic_expand_more);
            arrowIndicator.setContentDescription("展开标签");
        }
        
        tagsScrollView.setLayoutParams(params);
        
        // 确保按钮在状态改变后依然可见
        toggleTagsButton.bringToFront();
    }

    // 添加一个新的方法来检查标签高度和更新按钮可见性
    private void checkTagsVisibility() {
        if (tagsRecyclerView == null || toggleTagsButton == null) return;
        
        // 测量高度
        int height = tagsRecyclerView.getHeight();
        // 计算折叠高度（像素）
        float density = getResources().getDisplayMetrics().density;
        int collapsedHeightPx = (int) (COLLAPSED_HEIGHT_DP * density);
        
        // 如果内容高度大于折叠高度，显示按钮，否则隐藏
        toggleTagsButton.setVisibility(height > collapsedHeightPx ? View.VISIBLE : View.GONE);
        
        // 如果按钮可见，确保其状态与 isTagsExpanded 一致
        if (toggleTagsButton.getVisibility() == View.VISIBLE) {
            arrowIndicator.setImageResource(isTagsExpanded ? 
                R.drawable.ic_expand_less : R.drawable.ic_expand_more);
        }
    }

    // 修复 refreshTags 方法
    public void refreshTags() {
        // 重新加载标签或检查可见性
        if (tagsRecyclerView != null) {
            tagsRecyclerView.post(this::checkTagsVisibility);
        }
    }

    private void loadHighlightedTags() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        highlightedTags = new HashSet<>(prefs.getStringSet(PREF_HIGHLIGHTED_TAGS, new HashSet<>()));
    }

    private void toggleTagHighlight(String tagName) {
        // 如果标签已经高亮，则取消高亮
        if (highlightedTags.contains(tagName)) {
            highlightedTags.remove(tagName);
        } else {
            // 否则添加高亮
            highlightedTags.add(tagName);
        }
        
        // 保存更改到SharedPreferences
        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putStringSet(PREF_HIGHLIGHTED_TAGS, highlightedTags).apply();
        
        // 重新加载标签以应用新样式
        loadTags();
    }
} 