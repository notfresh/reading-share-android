package person.notfresh.myapplication.ui.gallery;

import android.content.Context;
import android.content.SharedPreferences;
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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.snackbar.Snackbar;

import person.notfresh.myapplication.R;
import person.notfresh.myapplication.adapter.LinksAdapter;
import person.notfresh.myapplication.db.LinkDao;
import person.notfresh.myapplication.model.LinkItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TagsFragment extends Fragment implements LinksAdapter.OnLinkActionListener {
    private static final String PREF_NAME = "TagsPreferences";
    private static final String KEY_LAST_TAG = "lastSelectedTag";
    private static final String KEY_SELECTED_TAGS = "selectedTags";
    private static final String KEY_NO_TAG_SELECTED = "noTagSelected";
    private static final String NO_TAG = "NO_TAG";  // 用于表示"无标签"选项
    
    private FlexboxLayout tagsContainer;
    private RecyclerView linksRecyclerView;
    private LinksAdapter linksAdapter;
    private LinkDao linkDao;
    private Set<TextView> selectedTags = new HashSet<>();  // 使用Set存储选中的标签

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
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);  // 启用选项菜单
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.tags_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_add_tag) {
            showAddTagDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAddTagDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_tag, null);
        EditText tagInput = dialogView.findViewById(R.id.edit_tag_name);

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
        List<String> tags = linkDao.getAllTags();
        Log.d("TagsFragment", "Loading tags: " + tags.size());
        tagsContainer.removeAllViews();
        
        // 添加"无标签"选项
        TextView noTagView = (TextView) getLayoutInflater()
                .inflate(R.layout.item_tag, tagsContainer, false);
        noTagView.setText("无标签");
        noTagView.setBackgroundResource(R.drawable.tag_background_normal);
        noTagView.setOnClickListener(v -> {
            Log.d("TagsFragment", "Clicked no tags");
            updateTagSelection(noTagView);
        });
        noTagView.setTag(NO_TAG);
        tagsContainer.addView(noTagView);
        
        // 添加其他标签
        for (String tag : tags) {
            Log.d("TagsFragment", "Adding tag: " + tag);
            TextView tagView = (TextView) getLayoutInflater()
                    .inflate(R.layout.item_tag, tagsContainer, false);
            tagView.setText(tag);
            tagView.setBackgroundResource(R.drawable.tag_background_normal);
            tagView.setOnClickListener(v -> {
                updateTagSelection(tagView);
            });
            tagView.setTag(tag);
            tagsContainer.addView(tagView);
        }
    }

    private void updateTagSelection(TextView tagView) {
        if (selectedTags.contains(tagView)) {
            // 取消选择这个标签
            tagView.setBackgroundResource(R.drawable.tag_background_normal);
            tagView.setTextColor(getResources().getColor(android.R.color.black, null));
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
            tagView.setTextColor(getResources().getColor(android.R.color.white, null));
            selectedTags.add(tagView);
            updateContentBySelectedTags();
        }
    }

    private void updateContentBySelectedTags() {
        Set<String> selectedTagNames = new HashSet<>();
        boolean hasNoTagFilter = false;

        // 收集选中的标签名
        for (TextView tagView : selectedTags) {
            String tag = (String) tagView.getTag();
            if (NO_TAG.equals(tag)) {
                hasNoTagFilter = true;
            } else {
                selectedTagNames.add(tag);
            }
        }

        // 获取符合条件的链接
        List<LinkItem> filteredLinks = new ArrayList<>();
        if (hasNoTagFilter) {
            filteredLinks.addAll(linkDao.getLinksWithoutTags());
        }
        if (!selectedTagNames.isEmpty()) {
            filteredLinks.addAll(linkDao.getLinksByTags(selectedTagNames));
        }
        if (selectedTags.isEmpty()) {
            // 如果没有选中的标签，显示所有内容
            filteredLinks = linkDao.getAllLinks();
        }

        // 更新标题
        updateTitle(selectedTagNames, hasNoTagFilter);

        // 更新显示
        linksAdapter.setLinks(filteredLinks);

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
            if (child instanceof TextView) {
                TextView tagView = (TextView) child;
                String tag = (String) tagView.getTag();
                if ((NO_TAG.equals(tag) && noTagSelected) || savedTags.contains(tag)) {
                    updateTagSelection(tagView);
                }
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
        linkDao.deleteLink(link.getUrl());
        // 重新加载当前标签的链接
        loadTags();
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
        TextView targetTagView = null;  // 用于存储要选中的标签视图
        
        // 2. 如果是新标签，先创建标签
        if (isNewTag) {
            long tagId = linkDao.addTag(tag);
            if (tagId != -1) {
                // 2.1 立即在顶部标签列表添加新标签
                TextView newTagView = (TextView) getLayoutInflater()
                        .inflate(R.layout.item_tag, tagsContainer, false);
                newTagView.setText(tag);
                newTagView.setBackgroundResource(R.drawable.tag_background_normal);
                newTagView.setOnClickListener(v -> updateTagSelection((TextView)v));  // 使用 view 参数
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
                if (child instanceof TextView) {
                    TextView tagView = (TextView) child;
                    if (tag.equals(tagView.getText().toString())) {
                        targetTagView = tagView;
                        break;
                    }
                }
            }
        }
        
        // 3. 为链接添加标签
        linkDao.addTagToLink(item.getId(), tag);
        
        // 4. 如果找到了目标标签视图，添加到选中集合（不清除现有选择）
        if (targetTagView != null && !selectedTags.contains(targetTagView)) {
            // 选中新标签，但保持其他标签的选中状态
            targetTagView.setBackgroundResource(R.drawable.tag_background_selected);
            targetTagView.setTextColor(getResources().getColor(android.R.color.white, null));
            selectedTags.add(targetTagView);
            
            // 更新顶栏标题
            Set<String> selectedTagNames = new HashSet<>();
            boolean hasNoTagFilter = false;
            for (TextView tagView : selectedTags) {
                String tagName = (String) tagView.getTag();
                if (NO_TAG.equals(tagName)) {
                    hasNoTagFilter = true;
                } else {
                    selectedTagNames.add(tagView.getText().toString());
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
        // 标签页不需要处理多选模式
    }
} 