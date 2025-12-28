package person.notfresh.readingshare.ui.home;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.snackbar.Snackbar;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.adapter.LinksAdapter;
import person.notfresh.readingshare.databinding.FragmentHomeBinding;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.db.SubjectDao;
import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.core.model.SubjectItem;
import person.notfresh.readingshare.core.model.SubjectUtil;
import person.notfresh.readingshare.ui.subject.SelectSubjectDialog;
import person.notfresh.readingshare.util.ExportUtil;
import person.notfresh.readingshare.ClickStatisticsActivity;
import person.notfresh.readingshare.util.ShareUtil;
import person.notfresh.readingshare.util.ImageUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HomeFragment extends Fragment implements LinksAdapter.OnLinkActionListener {

    private static final int REQUEST_CODE_PICK_ICON = 1001;

    private FragmentHomeBinding binding;
    private LinksAdapter adapter;
    private LinkDao linkDao;
    private boolean isSelectionMode = false;
    private MenuItem shareMenuItem;
    private MenuItem closeSelectionMenuItem;
    private MenuItem enterSelectionMenuItem;
    private EditText searchEditText;
    
    // 用于保存图片选择时的临时数据
    private LinkItem pendingIconItem;
    private String pendingIconUrl;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        Log.d("HomeFragment", "onCreate: setHasOptionsMenu(true)");
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        Log.d("HomeFragment", "onCreateOptionsMenu");
        menu.clear();
        inflater.inflate(R.menu.home_menu, menu);
        shareMenuItem = menu.findItem(R.id.action_share);
        closeSelectionMenuItem = menu.findItem(R.id.action_close_selection);
        enterSelectionMenuItem = menu.findItem(R.id.action_enter_selection);
        MenuItem statisticsMenuItem = menu.findItem(R.id.action_statistics);  // 新增的统计

        // 调整新增统计按钮的位置
        View actionView = requireActivity().findViewById(statisticsMenuItem.getItemId());
        if (actionView != null) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) actionView.getLayoutParams();
            params.rightMargin = getResources().getDimensionPixelSize(R.dimen.statistics_button_margin);
            actionView.setLayoutParams(params);
        }

       //新增的阅读量统计按钮
    //     MenuItem clickStatsButton = menu.findItem(R.id.action_statistics_read);
    //    clickStatsButton.setOnClickListener(v -> {
    //        // 打开阅读量统计页面
    //        Intent intent = new Intent(getActivity(), ClickStatisticsActivity.class);
    //        startActivity(intent);
    //    });

        shareMenuItem.setVisible(isSelectionMode);
        closeSelectionMenuItem.setVisible(isSelectionMode);
        if (enterSelectionMenuItem != null) {
            enterSelectionMenuItem.setVisible(!isSelectionMode);
        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_close_selection) {
            toggleSelectionMode();  // 退出选择模式
            return true;
        } else if (id == R.id.action_enter_selection) {
            toggleSelectionMode();  // 进入选择模式
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
        } else if (id == R.id.action_add_to_subject) {
            addToSubject();
            return true;
        } else if (id == R.id.action_statistics) {
            // 导航到统计页面
            Navigation.findNavController(requireView())
                     .navigate(R.id.action_nav_home_to_nav_statistics);
            return true;
        } else if (id == R.id.action_statistics_read) {
            // 打开阅读量统计页面
            Intent intent = new Intent(getActivity(), ClickStatisticsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                            ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // 检查是否有选定的日期
        String selectedDate = null;
        if (getArguments() != null) {
            selectedDate = getArguments().getString("selected_date");
        }

        linkDao = new LinkDao(requireContext());
        linkDao.open();

        RecyclerView recyclerView = binding.recyclerView;
        adapter = new LinksAdapter(requireContext());
        adapter.setOnLinkActionListener(this);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        // 启用滑动操作功能
        adapter.enableSwipeActions(recyclerView);

        // 设置 RecyclerView 的点击事件
        recyclerView.setOnTouchListener((v, event) -> {
            searchEditText.clearFocus();  // 让搜索框失去焦点
            return false;
        });

        // 加载置顶链接和普通链接
        List<LinkItem> pinnedLinks = linkDao.getPinnedLinks(); // 需要在 LinkDao 中添加此方法
        Map<String, List<LinkItem>> groupedLinks = linkDao.getLinksGroupByDate();
        
        // 设置置顶和普通链接到适配器
        adapter.setPinnedLinks(pinnedLinks); // 需要在 LinksAdapter 中添加此方法
        adapter.setGroupedLinks(groupedLinks);

        // 如果有选定日期，滚动到对应位置
        if (selectedDate != null) {
            scrollToDate(recyclerView, selectedDate);
        }

        // 设置搜索框
        searchEditText = binding.searchEditText;
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                adapter.filter(s.toString());
            }
        });

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        if (linkDao != null) {
            linkDao.close();
        }
    }

    public boolean deleteLink(Long linkId){
        Log.d("HomeFragment", "deleteLink: + link id " + linkId);
        linkDao.deleteLink(linkId);
        List<LinkItem> pinnedLinks = linkDao.getPinnedLinks();
        adapter.setPinnedLinks(pinnedLinks);
        // 重新加载分组链接
        Map<String, List<LinkItem>> groupedLinks = linkDao.getLinksGroupByDate();
        adapter.setGroupedLinks(groupedLinks);
        adapter.notifyDataSetChanged();
        return true;
    }

    @Override
    public void onDeleteLink(LinkItem link) {
        Log.d("HomeFragment", "onDeleteLink: " + link.getTitle() + ", link id " + link.getId());
        
        // 删除数据库中的链接
        linkDao.deleteLink(link.getId());
        
        // 直接从适配器中移除，避免重新查询数据库
        adapter.removeLinkItem(link);
        
        Log.d("HomeFragment", "链接删除完成，UI已更新");
    }

    @Override
    public void onUpdateLink(LinkItem oldLink, String newTitle) {
        linkDao.updateLinkTitle(oldLink.getUrl(), newTitle);
        // 刷新列表
        Map<String, List<LinkItem>> groupedLinks = linkDao.getLinksGroupByDate();
        adapter.setGroupedLinks(groupedLinks);
    }

    //@Override
    public void addTagToLink(LinkItem item, String tag) {
        linkDao.addTagToLink(item.getId(), tag);
        // 刷新列表
        Map<String, List<LinkItem>> groupedLinks = linkDao.getLinksGroupByDate();
        adapter.setGroupedLinks(groupedLinks);
    }

    @Override
    public void addTagsToLink(LinkItem item, List<String> tags) {

    }

    //@Override
    public void updateLinkTags(LinkItem item) {
        linkDao.updateLinkTags(item);
        // 刷新列表
        Map<String, List<LinkItem>> groupedLinks = linkDao.getLinksGroupByDate();
        adapter.setGroupedLinks(groupedLinks);
    }

    @Override
    public void onEnterSelectionMode() {
        if (!isSelectionMode) {
            toggleSelectionMode();
        }
    }

    private void toggleSelectionMode() {
        Log.d("HomeFragment", "toggleSelectionMode called");
        isSelectionMode = !isSelectionMode;
        adapter.toggleSelectionMode();
        if (shareMenuItem != null) {
            shareMenuItem.setVisible(isSelectionMode);
        }
        if (closeSelectionMenuItem != null) {
            closeSelectionMenuItem.setVisible(isSelectionMode);
        }
        if (enterSelectionMenuItem != null) {
            enterSelectionMenuItem.setVisible(!isSelectionMode);
        }
        // 更新标题
        if (isSelectionMode) {
            requireActivity().setTitle("选择要分享的链接");
        } else {
            requireActivity().setTitle(R.string.app_name);
        }
        requireActivity().invalidateOptionsMenu();
        Log.d("HomeFragment", "Selection mode: " + isSelectionMode);
    }

    private void shareAsText() {
        Set<LinkItem> selectedItems = adapter.getSelectedItems();
        ShareUtil.shareLinksAsText(requireContext(), new ArrayList<>(selectedItems));
    }

    private void shareAsFile(boolean isJson) {
        Set<LinkItem> selectedItems = adapter.getSelectedItems();
        ArrayList<LinkItem> items = new ArrayList<>(selectedItems);
        ShareUtil.shareLinksAsFileWithDialog(requireContext(), items, isJson, deleteAfterShare -> {
            if (deleteAfterShare) {
                // 批量删除并刷新
                for (LinkItem item : items) {
                    linkDao.deleteLink(item.getId());
                }
                List<LinkItem> pinnedLinks = linkDao.getPinnedLinks();
                adapter.setPinnedLinks(pinnedLinks);
                Map<String, List<LinkItem>> groupedLinks = linkDao.getLinksGroupByDate();
                adapter.setGroupedLinks(groupedLinks);
                adapter.notifyDataSetChanged();
                Toast.makeText(requireContext(), "已删除已分享的链接", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addToSubject() {
        Set<LinkItem> selectedItems = adapter.getSelectedItems();
        if (selectedItems.isEmpty()) {
            Toast.makeText(requireContext(), "请先选择要添加的链接", Toast.LENGTH_SHORT).show();
            return;
        }
        // 复用辅助方法
        addLinksToSubject(new ArrayList<>(selectedItems));
    }

    /**
     * 添加链接到主题（辅助方法，可被单个或多个链接调用）
     */
    private void addLinksToSubject(List<LinkItem> items) {
        if (items == null || items.isEmpty()) {
            Toast.makeText(requireContext(), "请先选择要添加的链接", Toast.LENGTH_SHORT).show();
            return;
        }

        // 获取链接ID列表
        List<Long> linkIds = new ArrayList<>();
        for (LinkItem item : items) {
            linkIds.add(item.getId());
        }

        // 显示选择主题对话框
        SelectSubjectDialog dialog = SelectSubjectDialog.newInstance(linkIds);
        dialog.setOnSubjectSelectedListener((subjectId, selectedLinkIds) -> {
            // 批量创建 SubjectItem
            SubjectDao subjectDao = new SubjectDao(requireContext());
            subjectDao.open();
            try {
                // 获取现有主题项，用于计算 orderIndex
                List<SubjectItem> existingItems = subjectDao.getSubjectItemsBySubjectId(subjectId);
                
                // 为每个链接创建 SubjectItem
                List<SubjectItem> newItems = new ArrayList<>();
                for (Long linkId : selectedLinkIds) {
                    SubjectItem item = new SubjectItem(subjectId);
                    item.setLinkId(linkId);
                    // 计算 orderIndex
                    int orderIndex = SubjectUtil.calculateOrderIndex(existingItems, -1);
                    item.setOrderIndex(orderIndex);
                    existingItems.add(item); // 添加到列表，用于下一个项的计算
                    newItems.add(item);
                }
                
                // 批量插入
                subjectDao.batchInsertSubjectItems(newItems);
                Toast.makeText(requireContext(), "已添加 " + newItems.size() + " 个链接到主题", Toast.LENGTH_SHORT).show();
            } finally {
                subjectDao.close();
            }
        });
        dialog.show(getParentFragmentManager(), "SelectSubjectDialog");
    }

    @Override
    public void onAddToSubject(LinkItem item) {
        // 单个链接添加到主题，复用辅助方法
        List<LinkItem> items = new ArrayList<>();
        items.add(item);
        addLinksToSubject(items);
    }

    @Override
    public void onPinStatusChanged() {
        Log.d("HomeFragment", "onPinStatusChanged 被调用");
        // 重新加载数据
        List<LinkItem> pinnedLinks = linkDao.getPinnedLinks();
        Map<String, List<LinkItem>> groupedLinks = linkDao.getLinksGroupByDate();
        
        Log.d("HomeFragment", "置顶链接数量: " + pinnedLinks.size());
        adapter.setPinnedLinks(pinnedLinks);
        adapter.setGroupedLinks(groupedLinks);
    }

    private void scrollToDate(RecyclerView recyclerView, String date) {
        // 找到日期对应的位置
        int position = adapter.getPositionForDate(date);
        if (position != -1) {
            recyclerView.post(() -> {
                // 获取 LinearLayoutManager
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null) {
                    // 获取搜索框的高度
                    int searchBoxHeight = binding.searchEditText.getHeight();
                    // 滚动到指定位置，offset 为搜索框高度
                    layoutManager.scrollToPositionWithOffset(position, searchBoxHeight);
                }
            });
        }
    }

    public void onLinkRemarkUpdated(LinkItem item) {
        // 如果需要刷新UI，可以在这里处理
        // 目前LinkDao中更新了数据库，而adapter中已经更新了视图，所以这里不需要额外操作
    }

    @Override
    public void onRequestCustomIcon(LinkItem item, String url) {
        Log.d("HomeFragment", "onRequestCustomIcon: " + item.getTitle() + ", URL: " + url);
        
        // 保存临时数据
        pendingIconItem = item;
        pendingIconUrl = url;
        
        // 打开相册选择图片
        Intent intent = ImageUtil.createGalleryPickerIntent();
        startActivityForResult(intent, REQUEST_CODE_PICK_ICON);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_CODE_PICK_ICON && resultCode == android.app.Activity.RESULT_OK) {
            if (data != null && data.getData() != null && pendingIconItem != null) {
                Uri selectedImageUri = data.getData();
                Log.d("HomeFragment", "图片选择成功: " + selectedImageUri);
                
                try {
                    // 1. 将 URI 转换为 Bitmap
                    Bitmap bitmap = ImageUtil.uriToBitmap(requireContext(), selectedImageUri);
                    
                    if (bitmap != null) {
                        // 2. 缩放为正方形（快捷方式图标需要 256x256）
                        Bitmap squareBitmap = ImageUtil.resizeToSquareForShortcut(bitmap);
                        
                        // 3. 释放原图内存
                        if (squareBitmap != bitmap) {
                            bitmap.recycle();
                        }
                        
                        // 4. 创建快捷方式
                        adapter.createShortcutWithCustomIcon(
                            requireContext(), 
                            pendingIconItem, 
                            pendingIconUrl, 
                            squareBitmap
                        );
                        
                        Log.d("HomeFragment", "快捷方式创建成功（使用自定义图标）");
                    } else {
                        Toast.makeText(requireContext(), "无法读取图片", Toast.LENGTH_SHORT).show();
                        Log.e("HomeFragment", "无法从 URI 读取图片");
                    }
                } catch (Exception e) {
                    Log.e("HomeFragment", "处理图片时出错: " + e.getMessage(), e);
                    Toast.makeText(requireContext(), "处理图片时出错: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                } finally {
                    // 清理临时数据
                    pendingIconItem = null;
                    pendingIconUrl = null;
                }
            } else {
                Log.w("HomeFragment", "图片选择失败：data 或 pendingIconItem 为 null");
                pendingIconItem = null;
                pendingIconUrl = null;
            }
        }
    }
}