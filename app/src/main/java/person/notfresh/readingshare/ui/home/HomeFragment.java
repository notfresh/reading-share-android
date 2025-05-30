package person.notfresh.readingshare.ui.home;

import android.content.ComponentName;
import android.content.Intent;
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
import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.util.ExportUtil;
import person.notfresh.readingshare.ClickStatisticsActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HomeFragment extends Fragment implements LinksAdapter.OnLinkActionListener {

    private FragmentHomeBinding binding;
    private LinksAdapter adapter;
    private LinkDao linkDao;
    private boolean isSelectionMode = false;
    private MenuItem shareMenuItem;
    private MenuItem closeSelectionMenuItem;
    private EditText searchEditText;

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

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_close_selection) {
            toggleSelectionMode();  // 退出选择模式
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

    @Override
    public void onDeleteLink(LinkItem link) {
        // linkDao.deleteLink(link.getUrl());
        linkDao.deleteLink(link.getId());
        // 刷新列表
        Map<String, List<LinkItem>> groupedLinks = linkDao.getLinksGroupByDate();
        adapter.setGroupedLinks(groupedLinks);
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
        
        // 创建选择器并排除自己的应用
        Intent chooserIntent = Intent.createChooser(shareIntent, "分享到");
        String myPackageName = requireContext().getPackageName();
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        chooserIntent.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, 
            new ComponentName[]{new ComponentName(myPackageName, myPackageName + ".MainActivity")});
        
        startActivity(chooserIntent);
    }

    private void shareAsFile(boolean isJson) {
        Set<LinkItem> selectedItems = adapter.getSelectedItems();
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
            
            // 创建选择器并排除自己的应用
            Intent chooserIntent = Intent.createChooser(shareIntent, "分享文件");
            String myPackageName = requireContext().getPackageName();
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            chooserIntent.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, 
                new ComponentName[]{new ComponentName(myPackageName, myPackageName + ".MainActivity")});
            
            startActivity(chooserIntent);
            
        } catch (Exception e) {
            Snackbar.make(requireView(), 
                "分享失败：" + e.getMessage(), 
                Snackbar.LENGTH_LONG).show();
        }
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
}