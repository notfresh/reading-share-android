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
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.snackbar.Snackbar;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.adapter.LinksAdapter;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.util.ExportUtil;

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
    
    private FlexboxLayout tagsContainer;
    private RecyclerView linksRecyclerView;
    private LinksAdapter linksAdapter;
    private LinkDao linkDao;
    private Set<View> selectedTags = new HashSet<>();  // 使用Set存储选中的标签
    private MenuItem shareMenuItem;
    private MenuItem closeSelectionMenuItem;
    private MenuItem selectAllMenuItem;  // 添加全选菜单项引用
    private boolean isSelectionMode = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                            Bundle savedInstanceState) {
        Log.d("TagsFragment", "onCreateView started");
        View root = inflater.inflate(R.layout.fragment_tags, container, false);

        tagsContainer = root.findViewById(R.id.tags_container);
        Log.d("TagsFragment", "tagsContainer found: " + (tagsContainer != null));
        
        linksRecyclerView = root.findViewById(R.id.links_recycler_view);
        Log.d("TagsFragment", "linksRecyclerView found: " + (linksRecyclerView != null));

        linkDao = new LinkDao(requireContext());
        linkDao.open();

        // 设置 RecyclerView
        linksAdapter = new LinksAdapter(requireContext());
        linksAdapter.setOnLinkActionListener(this);
        linksRecyclerView.setAdapter(linksAdapter);
        linksRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        // 启用滑动操作功能
        linksAdapter.enableSwipeActions(linksRecyclerView);

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
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);  // 启用选项菜单
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.tags_menu, menu);
        shareMenuItem = menu.findItem(R.id.action_share);
        closeSelectionMenuItem = menu.findItem(R.id.action_close_selection);
        selectAllMenuItem = menu.findItem(R.id.action_select_all);  // 获取全选菜单项
        shareMenuItem.setVisible(isSelectionMode);
        closeSelectionMenuItem.setVisible(isSelectionMode);
        selectAllMenuItem.setVisible(isSelectionMode);  // 设置全选按钮可见性
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_add_tag) {
            showAddTagDialog();
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

    private void loadTags() { // TODO:删除的时候没有刷新界面
        // 获取包含使用次数的标签集合
        Map<String, Integer> tagsWithCount = linkDao.getTagsWithCount();
        Log.d("TagsFragment", "Loading tags: " + tagsWithCount.size());
        tagsContainer.removeAllViews();
        
        // 添加"无标签"选项
        View noTagView = getLayoutInflater().inflate(R.layout.item_tag_with_count, tagsContainer, false);
        TextView tagText = noTagView.findViewById(R.id.text_tag);
        TextView countText = noTagView.findViewById(R.id.text_count);
        
        tagText.setText("无标签");
        
        // 获取无标签的链接数量
        int noTagCount = linkDao.getLinksWithoutTags().size();
        countText.setText(String.valueOf(noTagCount));
        
        noTagView.setBackgroundResource(R.drawable.tag_background_normal);
        noTagView.setOnClickListener(v -> {
            Log.d("TagsFragment", "Clicked no tags");
            updateTagSelection(v);
        });
        
        // 设置特殊标识，表示这是"无标签"选项
        setTagViewId(noTagView, NO_TAG);
        
        tagsContainer.addView(noTagView);
        
        // 添加其他标签及其使用次数
        for (Map.Entry<String, Integer> entry : tagsWithCount.entrySet()) {
            String tag = entry.getKey();
            int count = entry.getValue();
            Log.d("TagsFragment", "Adding tag: " + tag + " (count: " + count + ")");
            addTagView(tag, count, false);
        }
    }

    private void addTagView(String tag, int count, boolean isSelected) {
        // 使用自定义布局显示标签和使用次数
        View tagItemView = getLayoutInflater().inflate(R.layout.item_tag_with_count, tagsContainer, false);
        TextView tagText = tagItemView.findViewById(R.id.text_tag);
        TextView countText = tagItemView.findViewById(R.id.text_count);
        
        tagText.setText(tag);
        countText.setText(String.valueOf(count));
        
        if (isSelected) {
            tagItemView.setBackgroundResource(R.drawable.tag_background_selected);
        } else {
            tagItemView.setBackgroundResource(R.drawable.tag_background_normal);
        }
        
        // 使用设置标签名称的辅助方法
        setTagViewId(tagItemView, tag);
        
        tagItemView.setOnClickListener(v -> {
            updateTagSelection(tagItemView);
        });
        
        tagItemView.setOnLongClickListener(v -> {
            showTagOptionsDialog(tag);
            return true;
        });
        
        tagsContainer.addView(tagItemView);
    }

    /**
     * 处理标签点击事件
     * @param tag 标签名称
     * @param tagView 标签视图组件
     */
    private void handleTagClick(String tag, View tagView) {
        if (tag.equals(NO_TAG)) {
            Log.d("TagsFragment", "Clicked no tags");
        }
        updateTagSelection(tagView);

        // 保存选择状态
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_LAST_TAG, tag);
        editor.apply();
    }

    private void showTagOptionsDialog(String tag) {
        String[] options = {"删除标签", "发布到网站"};
        
        new AlertDialog.Builder(requireContext())
            .setTitle("标签操作")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: // 删除标签
                        confirmDeleteTag(tag);
                        break;
                    case 1: // 发布到网站
                        publishTagToWebsite(tag);
                        break;
                }
            })
            .show();
    }

    private void confirmDeleteTag(String tag) {
        try {
            new AlertDialog.Builder(requireContext())
                .setTitle("确认删除")
                .setMessage("确定要删除标签 \"" + tag + "\" 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    try {
                        Log.d("TagsFragment", "开始删除标签: " + tag);
                        
                        // 从数据库中删除标签
                        linkDao.deleteTag(tag);
                        Log.d("TagsFragment", "标签已从数据库删除");
                        
                        // 从当前选中的标签集合中移除
                        Iterator<View> iterator = selectedTags.iterator();
                        while (iterator.hasNext()) {
                            View tagView = iterator.next();
                            String viewTagName = getTagNameFromView(tagView);
                            if (tag.equals(viewTagName)) {
                                iterator.remove();
                                break;
                            }
                        }
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

    private void updateTagSelection(View tagView) {
        if (selectedTags.contains(tagView)) {
            // 取消选择这个标签
            tagView.setBackgroundResource(R.drawable.tag_background_normal);
            
            // 如果使用复合视图，需要更新文本颜色
            TextView tagText = tagView.findViewById(R.id.text_tag);
            if (tagText != null) {
                tagText.setTextColor(getResources().getColor(android.R.color.black, null));
            }

            selectedTags.remove(tagView);

            if (selectedTags.isEmpty()) {
                // 如果没有选中的标签，显示所有内容
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
            tagView.setBackgroundResource(R.drawable.tag_background_selected);
            
            // 如果使用复合视图，需要更新文本颜色
            TextView tagText = tagView.findViewById(R.id.text_tag);
            if (tagText != null) {
                tagText.setTextColor(getResources().getColor(android.R.color.white, null));
            }
            
            selectedTags.add(tagView);
            updateContentBySelectedTags();
        }
    }

    private void updateContentBySelectedTags() {
        List<LinkItem> links = new ArrayList<>();
        Set<String> selectedTagNames = new HashSet<>();
        boolean hasNoTagFilter = false;

        // 收集选中的标签名称
        for (View tagView : selectedTags) {
            String tagName = getTagNameFromView(tagView);
            if (NO_TAG.equals(tagName)) {
                hasNoTagFilter = true;
            } else {
                selectedTagNames.add(tagName);
            }
        }

        // 根据选择获取链接
        if (selectedTags.isEmpty()) {
            // 如果没有选中任何标签，显示所有链接
            links = linkDao.getAllLinks();
        } else {
            // 如果选中了"无标签"
            if (hasNoTagFilter) {
                links.addAll(linkDao.getLinksWithoutTags());
            }
            // 如果还选中了其他标签
            if (!selectedTagNames.isEmpty()) {
                links.addAll(linkDao.getLinksByTags(selectedTagNames));
            }
        }

        // 更新标题
        updateTitle(selectedTagNames, hasNoTagFilter);

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
        
        linksAdapter.setGroupedLinks(groupedLinks);

        // 保存选择状态
        saveSelections(selectedTagNames, hasNoTagFilter);
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
        for (int i = 0; i < tagsContainer.getChildCount(); i++) {
            View child = tagsContainer.getChildAt(i);
            String tagName = getTagNameFromView(child);
            
            // 对于无标签选项的处理
            if (NO_TAG.equals(tagName) && noTagSelected) {
                updateTagSelection(child);
                continue;
            }
            
            // 对于普通标签
            if (savedTags.contains(tagName)) {
                updateTagSelection(child);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (linkDao != null) {
            linkDao.close();
        }
    }

    // 实现 OnLinkActionListener 的方法
    @Override
    public void onDeleteLink(LinkItem link) {
        linkDao.deleteLink(link.getUrl()); //TODO:删除的时候没有刷新界面
        // 刷新列表
        loadTags(); // 使用已有的 loadTags() 方法重新加载标签和链接
        restoreSelections();
        linksAdapter.notifyDataSetChanged();
    }

    @Override
    public void onUpdateLink(LinkItem oldLink, String newTitle) {
        linkDao.updateLinkTitle(oldLink.getUrl(), newTitle);
        // 重新加载当前标签的链接
        loadTags();
    }

    @Override
    public void addTagToLink(LinkItem item, String tag) {
        // 1. 检查标签是否存在
        List<String> existingTags = linkDao.getAllTags();
        boolean isNewTag = !existingTags.contains(tag);
        View targetTagView = null;  // 用于存储要选中的标签视图
        
        // 2. 如果是新标签，先创建标签
        if (isNewTag) {
            long tagId = linkDao.addTag(tag);
            if (tagId != -1) {
                // 2.1 立即在顶部标签列表添加新标签 (使用新的复合视图布局)
                View newTagView = getLayoutInflater().inflate(R.layout.item_tag_with_count, tagsContainer, false);
                TextView tagText = newTagView.findViewById(R.id.text_tag);
                TextView countText = newTagView.findViewById(R.id.text_count);
                
                tagText.setText(tag);
                countText.setText("1"); // 新标签的初始计数为1
                
                newTagView.setBackgroundResource(R.drawable.tag_background_normal);
                newTagView.setOnClickListener(v -> updateTagSelection(v));
                newTagView.setTag(tag);
                tagsContainer.addView(newTagView);
                
                targetTagView = newTagView;  // 保存引用
                
                // 2.2 显示提示
                Snackbar.make(requireView(), "已创建新标签：" + tag, Snackbar.LENGTH_SHORT).show();
            }
        } else {
            // 如果标签已存在，找到它
            for (int i = 0; i < tagsContainer.getChildCount(); i++) {
                View child = tagsContainer.getChildAt(i);
                TextView tagText = child.findViewById(R.id.text_tag);
                if (tagText != null && tag.equals(tagText.getText().toString())) {
                    targetTagView = child;
                    
                    // 更新计数
                    TextView countText = child.findViewById(R.id.text_count);
                    if (countText != null) {
                        int currentCount = Integer.parseInt(countText.getText().toString());
                        countText.setText(String.valueOf(currentCount + 1));
                    }
                    break;
                }
            }
        }
        
        // 3. 为链接添加标签
        linkDao.addTagToLink(item.getId(), tag);
        
        // 4. 如果找到了目标标签视图，添加到选中集合（不清除现有选择）
        if (targetTagView != null && !selectedTags.contains(targetTagView)) {
            // 选中新标签，但保持其他标签的选中状态
            targetTagView.setBackgroundResource(R.drawable.tag_background_selected);
            
            TextView tagText = targetTagView.findViewById(R.id.text_tag);
            if (tagText != null) {
                tagText.setTextColor(getResources().getColor(android.R.color.white, null));
            }
            
            selectedTags.add(targetTagView);
            
            // 更新顶栏标题
            Set<String> selectedTagNames = new HashSet<>();
            boolean hasNoTagFilter = false;
            for (View tagView : selectedTags) {
                String tagName = getTagNameFromView(tagView);
                if (NO_TAG.equals(tagName)) {
                    hasNoTagFilter = true;
                } else {
                    selectedTagNames.add(tagName);
                }
            }
            updateTitle(selectedTagNames, hasNoTagFilter);
            
            // 更新显示内容
            updateContentBySelectedTags();
        }
        
        // 5. 显示提示
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

    private void shareAsText() {
        Set<LinkItem> selectedItems = linksAdapter.getSelectedItems();
        if (selectedItems.isEmpty()) {
            Toast.makeText(requireContext(), "请先选择要分享的链接", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder shareText = new StringBuilder();
        for (LinkItem item : selectedItems) {
            shareText.append(item.getTitle())
                    .append("\n")
                    .append(item.getUrl())
                    .append("\n\n");
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
        startActivity(Intent.createChooser(shareIntent, "分享到"));
    }

    private void shareAsFile(boolean isJson) {
        Set<LinkItem> selectedItems = linksAdapter.getSelectedItems();
        if (selectedItems.isEmpty()) {
            Toast.makeText(requireContext(), "请先选择要分享的链接", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String filePath;
            if (isJson) {
                filePath = ExportUtil.exportToJson(requireContext(), new ArrayList<>(selectedItems));
            } else {
                filePath = ExportUtil.exportToCsv(requireContext(), new ArrayList<>(selectedItems));
            }
            
            File file = new File(filePath);
            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                requireContext().getPackageName() + ".provider",
                file);
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("*/*");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "分享文件"));
            
        } catch (Exception e) {
            Snackbar.make(requireView(), 
                "分享失败：" + e.getMessage(), 
                Snackbar.LENGTH_LONG).show();
        }
    }

    private void selectAllItems() {
        // 获取当前标签过滤后的链接
        List<LinkItem> links;
        if (selectedTags.isEmpty()) {
            links = linkDao.getAllLinks();  // 如果没有选中标签，获取所有链接
        } else {
            // 收集选中的标签名称
            Set<String> selectedTagNames = new HashSet<>();
            boolean hasNoTagFilter = false;
            
            for (View tagView : selectedTags) {
                String tag = getTagNameFromView(tagView);
                if (NO_TAG.equals(tag)) {
                    hasNoTagFilter = true;
                } else {
                    selectedTagNames.add(tag);
                }
            }
            
            // 根据选中的标签获取链接
            links = new ArrayList<>();
            if (hasNoTagFilter) {
                links.addAll(linkDao.getLinksWithoutTags());
            }
            if (!selectedTagNames.isEmpty()) {
                links.addAll(linkDao.getLinksByTags(selectedTagNames));
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
        Set<String> selectedTagNames = new HashSet<>();
        boolean hasNoTagFilter = false;

        // 收集选中的标签名称
        for (View tagView : selectedTags) {
            String tag = getTagNameFromView(tagView);
            if (NO_TAG.equals(tag)) {
                hasNoTagFilter = true;
            } else {
                selectedTagNames.add(tag);
            }
        }

        List<LinkItem> links = new ArrayList<>();
        
        if (selectedTags.isEmpty()) {
            // 如果没有选中任何标签，显示所有链接
            links = linkDao.getAllLinks();
        } else {
            // 如果选中了"无标签"选项
            if (hasNoTagFilter) {
                links.addAll(linkDao.getLinksWithoutTags());
            }
            // 如果有选中的标签
            if (!selectedTagNames.isEmpty()) {
                links.addAll(linkDao.getLinksByTags(selectedTagNames));
            }
        }

        // 更新适配器数据
        linksAdapter.setLinks(links);
        linksAdapter.notifyDataSetChanged();
    }

    private void setTagViewId(View tagView, String tagName) {
        // 对于特殊的"无标签"，使用特殊标识
        if (NO_TAG.equals(tagName)) {
            tagView.setTag(TAG_VIEW_NO_TAG);
        } else {
            // 为普通标签视图设置标签名称作为id
            tagView.setTag(tagName);
        }
    }

    private String getTagNameFromView(View tagView) {
        // 首先检查是否是"无标签"视图
        if (TAG_VIEW_NO_TAG.equals(tagView.getTag())) {
            return NO_TAG;
        }
        
        // 对于普通标签，优先从内部TextView获取文本
        TextView tagText = tagView.findViewById(R.id.text_tag);
        if (tagText != null) {
            return tagText.getText().toString();
        }
        
        // 如果无法获取到标签名，返回一个默认值
        return "";
    }

    private boolean isNoTagView(View tagView) {
        return TAG_VIEW_NO_TAG.equals(tagView.getTag());
    }
} 