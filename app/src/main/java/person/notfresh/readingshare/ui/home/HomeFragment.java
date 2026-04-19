package person.notfresh.readingshare.ui.home;

import android.app.Activity;
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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexWrap;

import com.google.android.material.snackbar.Snackbar;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.adapter.LinksAdapter;
import person.notfresh.readingshare.adapter.TagsAdapter;
import person.notfresh.readingshare.databinding.FragmentHomeBinding;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.db.SubjectDao;
import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.core.model.SubjectItem;
import person.notfresh.readingshare.core.model.SubjectUtil;
import person.notfresh.readingshare.ui.subject.SelectSubjectDialog;
import person.notfresh.readingshare.ClickStatisticsActivity;
import person.notfresh.readingshare.util.ShareUtil;
import person.notfresh.readingshare.util.ImageUtil;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.text.SimpleDateFormat;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 首页Fragment
 * 
 * 功能说明：
 * - 显示所有链接，支持按日期分组和置顶显示
 * - 支持标签管理和筛选功能（从TagsFragment合并）
 * - 支持链接选择、分享、添加到主题等功能
 * - 支持搜索功能
 * 
 * 合并历史：
 * - 阶段0-2：将TagsFragment的功能合并到HomeFragment
 * - 阶段3：测试和优化
 * - 阶段4：清理废弃代码和更新文档
 * 
 * @see TagsFragment（已废弃，功能已合并到此Fragment）
 */
public class HomeFragment extends Fragment implements LinksAdapter.OnLinkActionListener {

    private static final String TAG = "HomeFragment";  // Logcat过滤关键字
    private static final int REQUEST_CODE_PICK_ICON = 1001;
    private static final Logger log = LoggerFactory.getLogger(HomeFragment.class);

    private FragmentHomeBinding binding;
    private LinksAdapter adapter;
    private LinkDao linkDao;
    private boolean isSelectionMode = false;
    private MenuItem shareMenuItem;
    private MenuItem closeSelectionMenuItem;
    private MenuItem enterSelectionMenuItem;
    private MenuItem selectAllMenuItem;  // 全选菜单项（从TagsFragment合并）
    private MenuItem addTagMenuItem;  // 添加标签菜单项（从TagsFragment合并）
    private MenuItem sortMenuItem;  // 排序菜单项（从TagsFragment合并）
    private MenuItem exitSortMenuItem;  // 退出排序菜单项（从TagsFragment合并）
    private EditText searchEditText;
    
    // 用于保存图片选择时的临时数据
    private LinkItem pendingIconItem;
    private String pendingIconUrl;
    
    // ========== 标签管理相关成员变量（从TagsFragment合并）==========
    private static final String PREF_NAME = "TagsPreferences";
    private static final String KEY_SELECTED_TAGS = "selectedTags";
    private static final String KEY_NO_TAG_SELECTED = "noTagSelected";
    private static final String NO_TAG = "NO_TAG";  // 用于表示"无标签"选项
    private static final String PREF_HIGHLIGHTED_TAGS = "highlighted_tags";
    private static final int FIXED_TAGS_COUNT = 10;  // 固定显示的标签数量
    private static final int COLLAPSED_HEIGHT_DP = 120;  // 折叠高度
    private static final int SORT_MODE_MAX_HEIGHT_DP = 400;  // 排序模式下的最大高度（dp）
    
    private RecyclerView tagsRecyclerView;
    private RecyclerView tagsRecyclerViewCollapsed;  // 折叠标签区
    private TagsAdapter tagsAdapter;
    private TagsAdapter tagsAdapterCollapsed;  // 折叠标签区的适配器
    private ItemTouchHelper itemTouchHelper;
    private View expandMoreTagsButton;  // 展开更多标签按钮
    private TextView textExpandMore;
    private ImageView iconExpandMore;
    private boolean isMoreTagsExpanded = false;  // 是否展开更多标签
    private Set<String> selectedTagNames = new HashSet<>();  // 使用Set存储选中的标签名称
    private boolean isSortMode = false;  // 排序模式状态
    private ScrollView tagsScrollView;
    private View toggleTagsButton;
    private ImageView arrowIndicator;
    private boolean isTagsExpanded = false;  // 默认折叠
    private Set<String> highlightedTags = new HashSet<>();  // 保存高亮的标签
    private View tagsContainer;  // 标签容器（用于控制显示/隐藏）

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: start");
        try {
            setHasOptionsMenu(true);
            Log.d(TAG, "onCreate: setHasOptionsMenu(true) success");
        } catch (Exception e) {
            Log.e(TAG, "onCreate: exception", e);
            throw e;
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        Log.d(TAG, "onCreateOptionsMenu: start");
        try {
            menu.clear();
            inflater.inflate(R.menu.home_menu, menu);
            Log.d(TAG, "onCreateOptionsMenu: menu loaded successfully");
            
            shareMenuItem = menu.findItem(R.id.action_share);
            closeSelectionMenuItem = menu.findItem(R.id.action_close_selection);
            enterSelectionMenuItem = menu.findItem(R.id.action_enter_selection);
            selectAllMenuItem = menu.findItem(R.id.action_select_all);  // 全选菜单项
            addTagMenuItem = menu.findItem(R.id.action_add_tag);  // 添加标签菜单项
            sortMenuItem = menu.findItem(R.id.action_sort_tags);  // 排序菜单项
            exitSortMenuItem = menu.findItem(R.id.action_exit_sort);  // 退出排序菜单项
            MenuItem statisticsMenuItem = menu.findItem(R.id.action_statistics);  // 新增的统计
            
            Log.d(TAG, "onCreateOptionsMenu: menu items found - share=" + (shareMenuItem != null) + 
                    ", close=" + (closeSelectionMenuItem != null) + 
                    ", enter=" + (enterSelectionMenuItem != null) +
                    ", selectAll=" + (selectAllMenuItem != null) +
                    ", addTag=" + (addTagMenuItem != null) +
                    ", sort=" + (sortMenuItem != null) +
                    ", exitSort=" + (exitSortMenuItem != null));

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

            // 阶段2：统一状态管理，使用统一的方法更新菜单可见性
            updateMenuVisibility();

            super.onCreateOptionsMenu(menu, inflater);
            Log.d(TAG, "onCreateOptionsMenu: completed");
        } catch (Exception e) {
            Log.e(TAG, "onCreateOptionsMenu: exception", e);
            // 不抛出异常，允许继续执行
            super.onCreateOptionsMenu(menu, inflater);
        }
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
        } else if (id == R.id.action_add_tag) {
            // 添加标签（从TagsFragment合并）
            showAddTagDialog();
            return true;
        } else if (id == R.id.action_sort_tags) {
            // 进入排序模式（从TagsFragment合并）
            toggleSortMode();
            return true;
        } else if (id == R.id.action_exit_sort) {
            // 退出排序模式（从TagsFragment合并）
            toggleSortMode();
            return true;
        } else if (id == R.id.action_select_all) {
            // 全选（从TagsFragment合并）
            selectAllItems();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                            ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView: start");
        try {
            binding = FragmentHomeBinding.inflate(inflater, container, false);
            View root = binding.getRoot();
            Log.d(TAG, "onCreateView: layout loaded successfully");

            // 检查是否有选定的日期
            String selectedDate = null;
            if (getArguments() != null) {
                selectedDate = getArguments().getString("selected_date");
            }

            Log.d(TAG, "onCreateView: initializing LinkDao");
            linkDao = new LinkDao(requireContext());
            linkDao.open();
            Log.d(TAG, "onCreateView: LinkDao opened successfully");

            Log.d(TAG, "onCreateView: initializing RecyclerView and Adapter");
            RecyclerView recyclerView = binding.recyclerView;
            if (recyclerView == null) {
                Log.e(TAG, "onCreateView: recyclerView is null!");
                throw new IllegalStateException("recyclerView为null");
            }
            
            adapter = new LinksAdapter(requireContext());
            adapter.setOnLinkActionListener(this);
            recyclerView.setAdapter(adapter);
            recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
            Log.d(TAG, "onCreateView: RecyclerView setup completed");

            // 启用滑动操作功能
            adapter.enableSwipeActions(recyclerView);

            // 设置 RecyclerView 的点击事件
            recyclerView.setOnTouchListener((v, event) -> {
                if (searchEditText != null) {
                    searchEditText.clearFocus();  // 让搜索框失去焦点
                }
                return false;
            });

            // 设置搜索框
            Log.d(TAG, "onCreateView: initializing search box");
            searchEditText = binding.searchEditText;
            if (searchEditText != null) {
                searchEditText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        if (adapter != null) {
                            adapter.filter(s.toString());
                        }
                    }
                });
            } else {
                Log.w(TAG, "onCreateView: searchEditText is null");
            }

            // ========== 初始化标签管理UI（阶段2启用）==========
            Log.d(TAG, "onCreateView: initializing tag management UI");
            try {
                initTagRecyclerView(root);
                Log.d(TAG, "onCreateView: tag management UI initialized successfully");
            } catch (Exception e) {
                Log.e(TAG, "onCreateView: tag management UI initialization failed", e);
                // 不抛出异常，允许继续执行
            }
            
            // ========== 统一数据加载（阶段2）==========
            Log.d(TAG, "onCreateView: start loading data, selectedTagNames.size=" + selectedTagNames.size());
            
            // 注意：数据加载延迟到onViewCreated，确保RecyclerView已经完成布局
            
            // 异步加载标签列表，并在完成后恢复选择状态
            // 如果恢复了标签选择，会触发过滤，覆盖之前的所有链接显示
            Log.d(TAG, "onCreateView: start async loading tag list");
            try {
                loadTags();
            } catch (Exception e) {
                Log.e(TAG, "onCreateView: failed to load tag list", e);
                // 如果加载标签失败，至少已经显示了所有链接，不影响用户体验
            }

            // 如果有选定日期，滚动到对应位置
            if (selectedDate != null) {
                scrollToDate(recyclerView, selectedDate);
            }

            Log.d(TAG, "onCreateView: completed");
            return root;
        } catch (Exception e) {
            Log.e(TAG, "onCreateView: exception occurred", e);
            throw e;
        }
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated: start");
        
        // 在onViewCreated中直接加载所有链接，此时RecyclerView已经完成布局测量
        // 初始化时直接加载所有链接，确保用户能立即看到内容
        if (linkDao != null && adapter != null) {
            Log.d(TAG, "onViewCreated: loading all links directly");
            List<LinkItem> pinnedLinks = linkDao.getPinnedLinks();
            Map<String, List<LinkItem>> groupedLinks = linkDao.getLinksGroupByDate();
            
            // 计算总链接数
            int totalLinks = pinnedLinks.size();
            for (List<LinkItem> links : groupedLinks.values()) {
                totalLinks += links.size();
            }
            
            Log.d(TAG, "onViewCreated: loaded links - pinned=" + pinnedLinks.size() 
                    + ", groups=" + groupedLinks.size() + ", total links=" + totalLinks);
            
            // 直接设置数据到adapter
            adapter.setPinnedLinks(pinnedLinks);
            adapter.setGroupedLinks(groupedLinks);
            Log.d(TAG, "onViewCreated: data set to adapter, itemCount=" + adapter.getItemCount());
            
            // 检查RecyclerView状态
            RecyclerView recyclerView = binding.recyclerView;
            if (recyclerView != null) {
                Log.d(TAG, "onViewCreated: RecyclerView state - visibility=" + recyclerView.getVisibility() 
                        + " (0=VISIBLE), width=" + recyclerView.getWidth() 
                        + ", height=" + recyclerView.getHeight()
                        + ", isShown=" + recyclerView.isShown()
                        + ", hasFixedSize=" + recyclerView.hasFixedSize());
                
                // 确保RecyclerView可见
                if (recyclerView.getVisibility() != View.VISIBLE) {
                    Log.w(TAG, "onViewCreated: RecyclerView is not visible, setting to VISIBLE");
                    recyclerView.setVisibility(View.VISIBLE);
                }
                
                // 检查LayoutManager
                RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
                if (layoutManager != null) {
                    Log.d(TAG, "onViewCreated: LayoutManager exists, itemCount=" + layoutManager.getItemCount());
                } else {
                    Log.e(TAG, "onViewCreated: LayoutManager is null!");
                }
                
                // 强制刷新
                recyclerView.post(() -> {
                    Log.d(TAG, "onViewCreated: post - RecyclerView width=" + recyclerView.getWidth() 
                            + ", height=" + recyclerView.getHeight()
                            + ", adapter itemCount=" + (adapter != null ? adapter.getItemCount() : 0));
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                        Log.d(TAG, "onViewCreated: post - notifyDataSetChanged called");
                    }
                });
            } else {
                Log.e(TAG, "onViewCreated: RecyclerView is null!");
            }
        } else {
            Log.e(TAG, "onViewCreated: linkDao or adapter is null!");
        }
        
        // 检查是否有选定的日期，滚动到对应位置
        if (getArguments() != null) {
            String selectedDate = getArguments().getString("selected_date");
            if (selectedDate != null && binding != null) {
                RecyclerView recyclerView = binding.recyclerView;
                if (recyclerView != null) {
                    scrollToDate(recyclerView, selectedDate);
                }
            }
        }
        
        Log.d(TAG, "onViewCreated: completed");
    }
    
    // ========== 标签管理：加载与初始化 ==========
    
    /**
     * 初始化标签RecyclerView
     * 从TagsFragment合并，阶段2启用标签区域显示
     */
    private void initTagRecyclerView(View root) {
        Log.d(TAG, "initTagRecyclerView: start");
        try {
            if (root == null) {
                Log.e(TAG, "initTagRecyclerView: root is null!");
                return;
            }
            
            // 获取标签容器（阶段2启用显示）
            tagsContainer = root.findViewById(R.id.tags_container);
            if (tagsContainer != null) {
                tagsContainer.setVisibility(View.VISIBLE);  // 阶段2：启用标签区域显示
                Log.d(TAG, "initTagRecyclerView: tags container displayed");
            } else {
                Log.w(TAG, "initTagRecyclerView: tagsContainer is null, may not exist in layout");
            }
        
            // 初始化固定标签 RecyclerView
            Log.d(TAG, "initTagRecyclerView: initializing fixed tags RecyclerView");
            tagsRecyclerView = root.findViewById(R.id.recycler_tags_fixed);
            if (tagsRecyclerView != null) {
                try {
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
                    Log.d(TAG, "initTagRecyclerView: fixed tags RecyclerView initialized successfully");
                } catch (Exception e) {
                    Log.e(TAG, "initTagRecyclerView: fixed tags RecyclerView initialization failed", e);
                }
            } else {
                Log.w(TAG, "initTagRecyclerView: recycler_tags_fixed is null");
            }
        
            // 初始化折叠标签 RecyclerView
            Log.d(TAG, "initTagRecyclerView: initializing collapsed tags RecyclerView");
            tagsRecyclerViewCollapsed = root.findViewById(R.id.recycler_tags_collapsed);
            if (tagsRecyclerViewCollapsed != null) {
                try {
                    FlexboxLayoutManager layoutManagerCollapsed = new FlexboxLayoutManager(requireContext());
                    layoutManagerCollapsed.setFlexDirection(FlexDirection.ROW);
                    layoutManagerCollapsed.setFlexWrap(FlexWrap.WRAP);
                    tagsRecyclerViewCollapsed.setLayoutManager(layoutManagerCollapsed);
                    
                    tagsAdapterCollapsed = new TagsAdapter();
                    tagsAdapterCollapsed.setOnTagClickListener((position, tag) -> {
                        if (!isSortMode) {
                            handleTagClick(tag.getName());
                        }
                    });
                    tagsAdapterCollapsed.setOnTagLongClickListener((position, tag) -> {
                        if (!isSortMode) {
                            showTagOptionsDialog(tag.getName());
                        }
                    });
                    tagsRecyclerViewCollapsed.setAdapter(tagsAdapterCollapsed);
                    Log.d(TAG, "initTagRecyclerView: collapsed tags RecyclerView initialized successfully");
                } catch (Exception e) {
                    Log.e(TAG, "initTagRecyclerView: collapsed tags RecyclerView initialization failed", e);
                }
            } else {
                Log.w(TAG, "initTagRecyclerView: recycler_tags_collapsed is null");
            }
        
        // 初始化展开更多标签按钮
        expandMoreTagsButton = root.findViewById(R.id.btn_expand_more_tags);
        if (expandMoreTagsButton != null) {
            textExpandMore = root.findViewById(R.id.text_expand_more);
            iconExpandMore = root.findViewById(R.id.icon_expand_more);
            expandMoreTagsButton.setOnClickListener(v -> toggleMoreTagsExpansion());
        }
        
        // 初始化 ItemTouchHelper（用于排序模式）
        if (tagsRecyclerView != null) {
            ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN | 
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT,
                0
            ) {
                @Override
                public boolean onMove(@NonNull RecyclerView recyclerView, 
                                     @NonNull RecyclerView.ViewHolder viewHolder, 
                                     @NonNull RecyclerView.ViewHolder target) {
                    if (isSortMode && recyclerView == tagsRecyclerView) {
                        int fromPos = viewHolder.getAdapterPosition();
                        int toPos = target.getAdapterPosition();
                        tagsAdapter.swapItems(fromPos, toPos);
                        saveTagOrderToDatabase();
                        return true;
                    }
                    return false;
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
            itemTouchHelper.attachToRecyclerView(tagsRecyclerView);
        }
        
        // 获取标签容器和展开/折叠相关视图
        tagsScrollView = root.findViewById(R.id.tags_scrollview);
        toggleTagsButton = root.findViewById(R.id.btn_toggle_tags);
        arrowIndicator = root.findViewById(R.id.arrow_indicator);
        
        if (tagsScrollView != null) {
            ViewGroup.LayoutParams params = tagsScrollView.getLayoutParams();
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            tagsScrollView.setLayoutParams(params);
        }
        
        if (toggleTagsButton != null) {
            toggleTagsButton.setOnClickListener(v -> toggleTagsExpansion());
        }
        
        if (arrowIndicator != null) {
            arrowIndicator.setImageResource(R.drawable.ic_expand_less);
            arrowIndicator.setContentDescription("收起标签");
        }
        
            // 加载高亮标签设置
            try {
                loadHighlightedTags();
            } catch (Exception e) {
                Log.e(TAG, "initTagRecyclerView: failed to load highlighted tags settings", e);
            }
            
            Log.d(TAG, "initTagRecyclerView: completed");
        } catch (Exception e) {
            Log.e(TAG, "initTagRecyclerView: exception occurred", e);
            throw e;
        }
    }

    @Override
    public void onDestroyView() {
        Log.d(TAG, "onDestroyView: start");
        super.onDestroyView();
        binding = null;
        if (linkDao != null) {
            linkDao.close();
            Log.d(TAG, "onDestroyView: LinkDao closed");
        }
        Log.d(TAG, "onDestroyView: completed");
    }

    // ========== 实现接口方法：链接操作 ==========
    
    @Override
    public boolean deleteLink(Long linkId){
        Log.d("HomeFragment", "deleteLink: + link id " + linkId);
        linkDao.deleteLink(linkId);
        refreshLinksList();
        return true;
    }

    @Override
    public void onDeleteLink(LinkItem link) {
        Log.d("HomeFragment", "onDeleteLink: " + link.getTitle() + ", link id " + link.getId());
        
        // 删除数据库中的链接
        linkDao.deleteLink(link.getId());
        
        // 直接从适配器中移除，避免重新查询数据库
        adapter.removeLinkItem(link);
        
        // 如果标签区域可见，重新加载标签（更新标签计数）
        if (tagsContainer != null && tagsContainer.getVisibility() == View.VISIBLE) {
            loadTags();
        }
        
        Log.d("HomeFragment", "Link deletion completed, UI updated");
    }

    @Override
    public void onUpdateLink(LinkItem oldLink, String newTitle) {
        linkDao.updateLinkTitle(oldLink.getUrl(), newTitle);
        // 统一刷新：根据是否有标签筛选来决定刷新方式
        refreshLinksList();
    }

    //@Override
    public void addTagToLink(LinkItem item, String tag) {
        linkDao.addTagToLink(item.getId(), tag);
        // 统一刷新：根据是否有标签筛选来决定刷新方式
        refreshLinksList();
        // 如果标签区域可见，重新加载标签（更新标签计数）
        if (tagsContainer != null && tagsContainer.getVisibility() == View.VISIBLE) {
            loadTags();
        }
    }

    @Override
    public void addTagsToLink(LinkItem item, List<String> tags) {
        // TODO: 实现批量添加标签逻辑
    }

    //@Override
    public void updateLinkTags(LinkItem item) {
        linkDao.updateLinkTags(item);
        // 统一刷新：根据是否有标签筛选来决定刷新方式
        refreshLinksList();
        // 如果标签区域可见，重新加载标签（更新标签计数）
        if (tagsContainer != null && tagsContainer.getVisibility() == View.VISIBLE) {
            loadTags();
        }
    }

    @Override
    public void onEnterSelectionMode() {
        if (!isSelectionMode) {
            toggleSelectionMode();
        }
    }

    // ========== 选择模式：状态管理 ==========
    
    /**
     * 切换选择模式
     * 统一的选择模式切换逻辑，支持全选菜单项（如果存在）
     * 阶段2：统一状态管理，整合选择模式和排序模式
     */
    private void toggleSelectionMode() {
        Log.d("HomeFragment", "toggleSelectionMode called");
        isSelectionMode = !isSelectionMode;
        adapter.toggleSelectionMode();
        
        // 统一更新菜单项可见性
        updateMenuVisibility();
        
        // 更新标题
        if (isSelectionMode) {
            requireActivity().setTitle("选择要分享的链接");
        } else {
            // 根据是否有标签筛选来更新标题
            if (selectedTagNames.isEmpty()) {
                requireActivity().setTitle(R.string.app_name);
            } else {
                // 标题已在updateContentBySelectedTags中更新
            }
        }
        requireActivity().invalidateOptionsMenu();
        Log.d("HomeFragment", "Selection mode: " + isSelectionMode);
    }
    
    /**
     * 统一更新菜单项可见性
     * 阶段2：统一状态管理，根据选择模式、排序模式、标签选择状态统一管理菜单显示
     */
    private void updateMenuVisibility() {
        // 选择模式相关菜单项
        if (shareMenuItem != null) {
            shareMenuItem.setVisible(isSelectionMode);
        }
        if (closeSelectionMenuItem != null) {
            closeSelectionMenuItem.setVisible(isSelectionMode);
        }
        if (enterSelectionMenuItem != null) {
            enterSelectionMenuItem.setVisible(!isSelectionMode && !isSortMode);
        }
        if (selectAllMenuItem != null) {
            selectAllMenuItem.setVisible(isSelectionMode);
        }
        
        // 标签管理菜单项（仅在非选择模式、非排序模式下显示，且标签区域可见时）
        boolean tagsVisible = tagsContainer != null && tagsContainer.getVisibility() == View.VISIBLE;
        if (addTagMenuItem != null) {
            addTagMenuItem.setVisible(tagsVisible && !isSelectionMode && !isSortMode);
        }
        if (sortMenuItem != null) {
            sortMenuItem.setVisible(tagsVisible && !isSelectionMode && !isSortMode);
        }
        if (exitSortMenuItem != null) {
            exitSortMenuItem.setVisible(isSortMode);
        }
    }
    
    /**
     * 切换排序模式
     * 从TagsFragment合并，用于标签排序
     * 阶段2：统一状态管理，整合排序模式和选择模式
     */
    private void toggleSortMode() {
        isSortMode = !isSortMode;
        
        // 如果进入排序模式，先退出选择模式（两者不能同时存在）
        if (isSortMode && isSelectionMode) {
            isSelectionMode = false;
            adapter.toggleSelectionMode();
        }
        
        // 统一更新菜单项可见性
        updateMenuVisibility();
        
        // 启用/禁用拖拽功能
        if (itemTouchHelper != null) {
            if (isSortMode) {
                // 排序模式下，只附加到固定标签区（因为所有标签都在这里）
                itemTouchHelper.attachToRecyclerView(tagsRecyclerView);
            } else {
                itemTouchHelper.attachToRecyclerView(null);
            }
        }
        
        // 显示/隐藏底部收起按钮（排序模式下隐藏）
        if (toggleTagsButton != null) {
            toggleTagsButton.setVisibility(isSortMode ? View.GONE : View.VISIBLE);
        }
        
        // 调整标签区域高度
        updateTagsScrollViewHeight();
        
        // 重新加载标签（排序模式下显示所有标签，非排序模式下分成两部分）
        loadTags();
        
        // 更新标题
        if (isSortMode) {
            requireActivity().setTitle("拖拽排序标签");
            Toast.makeText(requireContext(), "长按标签可拖拽排序", Toast.LENGTH_SHORT).show();
        } else {
            // 根据是否有标签筛选来更新标题
            if (selectedTagNames.isEmpty()) {
                requireActivity().setTitle(R.string.app_name);
            } else {
                // 标题已在updateContentBySelectedTags中更新
            }
        }
        
        requireActivity().invalidateOptionsMenu();
    }

    // ========== 选择模式：分享与导出 ==========
    
    /**
     * 文本分享
     * 分享选中的链接为文本格式
     */
    private void shareAsText() {
        Set<LinkItem> selectedItems = adapter.getSelectedItems();
        ShareUtil.shareLinksAsText(requireContext(), new ArrayList<>(selectedItems));
    }

    /**
     * 文件分享
     * 分享选中的链接为文件格式（JSON或CSV）
     * @param isJson true表示JSON格式，false表示CSV格式
     */
    private void shareAsFile(boolean isJson) {
        Set<LinkItem> selectedItems = adapter.getSelectedItems();
        ArrayList<LinkItem> items = new ArrayList<>(selectedItems);
        ShareUtil.shareLinksAsFileWithDialog(requireContext(), items, isJson, deleteAfterShare -> {
            if (deleteAfterShare) {
                // 批量删除并刷新（优化：直接从适配器移除，避免重新查询数据库）
                for (LinkItem item : items) {
                    linkDao.deleteLink(item.getId());
                    adapter.removeLinkItem(item);
                }
                // 刷新数据
                refreshLinksList();
                Toast.makeText(requireContext(), "已删除已分享的链接", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 添加到主题
     * 将选中的链接添加到主题
     */
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
     * 全选
     * 选中当前显示的所有链接（包括筛选后的链接）
     * 阶段2：根据是否有标签筛选来决定全选范围
     */
    private void selectAllItems() {
        List<LinkItem> links;
        
        if (selectedTagNames.isEmpty()) {
            // 如果没有选中标签，获取所有链接
            links = linkDao.getAllLinks();
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
            adapter.selectItem(item);
        }
        adapter.notifyDataSetChanged();
    }

    /**
     * 添加链接到主题（辅助方法，可被单个或多个链接调用）
     * 统一的添加到主题逻辑
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
        Log.d("HomeFragment", "onPinStatusChanged called");
        // 统一刷新：根据是否有标签筛选来决定刷新方式
        refreshLinksList();
    }
    
    // ========== 链接列表：数据刷新 ==========
    
    /**
     * 刷新链接列表
     * 统一的数据刷新方法：根据是否有标签筛选来决定加载方式
     * - 如果没有选中标签：使用原有逻辑（显示所有链接）
     * - 如果选中了标签：使用筛选逻辑（显示筛选后的链接）
     */
    private void refreshLinksList() {
        Log.d(TAG, "refreshLinksList: start, selectedTagNames.size=" + selectedTagNames.size());
        try {
            if (linkDao == null) {
                Log.e(TAG, "refreshLinksList: linkDao is null!");
                return;
            }
            if (adapter == null) {
                Log.e(TAG, "refreshLinksList: adapter is null!");
                return;
            }
            
            if (selectedTagNames.isEmpty()) {
                // 原有逻辑：加载所有链接（HomeFragment原有行为）
                List<LinkItem> pinnedLinks = linkDao.getPinnedLinks();
                Map<String, List<LinkItem>> groupedLinks = linkDao.getLinksGroupByDate();
                
                // 计算总链接数
                int totalLinks = pinnedLinks.size();
                for (List<LinkItem> links : groupedLinks.values()) {
                    totalLinks += links.size();
                }
                
                Log.d(TAG, "refreshLinksList: refresh link list (all): pinned=" + pinnedLinks.size() 
                    + ", groups=" + groupedLinks.size() + ", total links=" + totalLinks);
                
                if (totalLinks == 0) {
                    Log.w(TAG, "refreshLinksList: WARNING! No link data found");
                }
                
                // 直接设置数据到adapter（onCreateView时RecyclerView已经准备好）
                Log.d(TAG, "refreshLinksList: setting data to adapter directly");
                adapter.setPinnedLinks(pinnedLinks);
                adapter.setGroupedLinks(groupedLinks);
                // setGroupedLinks内部已经调用了notifyDataSetChanged，不需要再次调用
                Log.d(TAG, "refreshLinksList: data set to adapter, notifyDataSetChanged called");
                
                // 验证数据是否设置成功
                RecyclerView recyclerView = binding.recyclerView;
                if (recyclerView != null && adapter != null) {
                    Log.d(TAG, "refreshLinksList: RecyclerView visibility=" + recyclerView.getVisibility() 
                            + ", width=" + recyclerView.getWidth() + ", height=" + recyclerView.getHeight()
                            + ", adapter itemCount=" + adapter.getItemCount());
                }
            } else {
                // 新逻辑：根据选中的标签筛选
                Log.d(TAG, "refreshLinksList: use tag filter logic");
                updateContentBySelectedTags();
            }
            Log.d(TAG, "refreshLinksList: completed");
        } catch (Exception e) {
            Log.e(TAG, "refreshLinksList: failed to refresh link list", e);
        }
    }
    
    // ========== 链接筛选：数据处理（从TagsFragment提取，阶段一使用）==========
    
    /**
     * 按日期分组链接
     * 将链接列表按日期分组，返回TreeMap（日期为key，链接列表为value）
     * @param links 要分组的链接列表
     * @return 按日期分组的Map，日期倒序排列
     */
    private Map<String, List<LinkItem>> groupLinksByDate(List<LinkItem> links) {
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
        
        return groupedLinks;
    }
    
    /**
     * 计算置顶链接与筛选结果的交集
     * 仅返回在筛选结果中存在的置顶链接
     * @param filteredLinks 筛选后的链接列表
     * @return 置顶链接与筛选结果的交集
     */
    private List<LinkItem> calculatePinnedOverlap(List<LinkItem> filteredLinks) {
        // 使用链接 id 作为交集判断依据
        Set<Long> filteredIds = new HashSet<>();
        for (LinkItem item : filteredLinks) {
            filteredIds.add(item.getId());
        }
        
        List<LinkItem> pinned = linkDao.getPinnedLinks();
        List<LinkItem> pinnedOverlap = new ArrayList<>();
        for (LinkItem p : pinned) {
            if (filteredIds.contains(p.getId())) {
                pinnedOverlap.add(p);
            }
        }
        
        return pinnedOverlap;
    }

    // ========== 链接列表：导航功能 ==========
    
    /**
     * 滚动到指定日期
     * 根据日期参数滚动到对应的链接位置
     */
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
                Log.d("HomeFragment", "Image selection successful: " + selectedImageUri);
                
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
                        
                        Log.d("HomeFragment", "Shortcut created successfully (using custom icon)");
                    } else {
                        Toast.makeText(requireContext(), "无法读取图片", Toast.LENGTH_SHORT).show();
                        Log.e("HomeFragment", "Failed to read image from URI");
                    }
                } catch (Exception e) {
                    Log.e("HomeFragment", "Error processing image: " + e.getMessage(), e);
                    Toast.makeText(requireContext(), "处理图片时出错: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                } finally {
                    // 清理临时数据
                    pendingIconItem = null;
                    pendingIconUrl = null;
                }
            } else {
                Log.w("HomeFragment", "Image selection failed: data or pendingIconItem is null");
                pendingIconItem = null;
                pendingIconUrl = null;
            }
        }
    }
    
    // ========== 标签管理：用户交互处理（从TagsFragment合并）==========
    
    /**
     * 处理标签点击
     * 当用户点击标签时调用
     */
    private void handleTagClick(String tagName) {
        if (NO_TAG.equals(tagName)) {
            Log.d("HomeFragment", "Clicked no tags");
            updateTagSelectionByName(tagName);
        } else {
            updateTagSelectionByName(tagName);
        }
    }
    
    /**
     * 更新标签选择状态
     * 根据标签名称添加或移除选择，并更新内容显示
     * 阶段2：统一状态管理，整合标签选择状态和内容更新
     */
    private void updateTagSelectionByName(String tagName) {
        // 先清理 selectedTagNames 中的无效标签（tagID为-1的标签）
        cleanInvalidTagsFromSelection();
        
        if (selectedTagNames.contains(tagName)) {
            // 取消选择
            selectedTagNames.remove(tagName);
            Log.d(TAG, "updateTagSelectionByName: unselect tag, tagName=" + tagName);
            Log.d(TAG, "updateTagSelectionByName: selectedTagNames.size=" + selectedTagNames.size());
            for (String tag : selectedTagNames) {
                Log.d(TAG, "updateTagSelectionByName: tag=" + tag);
            }
            if (selectedTagNames.isEmpty()) {
                // 如果没有选中任何标签，显示所有链接（HomeFragment原有行为）
                requireActivity().setTitle("全部内容");
                refreshLinksList();  // 使用统一方法刷新
                clearSavedSelections();
            } else {
                // 根据剩余选中的标签筛选内容
                updateContentBySelectedTags();
            }
        } else {
            Log.d(TAG, "updateTagSelectionByName: select tag, tagName=" + tagName);
            
            // 验证要添加的标签是否存在
            if (NO_TAG.equals(tagName)) {
                // NO_TAG 特殊处理，总是允许选择（即使数量为0）
                selectedTagNames.add(tagName);
                Log.d(TAG, "updateTagSelectionByName: selectedTagNames.size=" + selectedTagNames.size());
                updateContentBySelectedTags();
            } else {
                // 验证普通标签是否存在
                try {
                    long tagId = linkDao.getTagIdByName(tagName);
                    if (tagId != -1) {
                        // 标签存在，可以添加
                        selectedTagNames.add(tagName);
                        Log.d(TAG, "updateTagSelectionByName: selectedTagNames.size=" + selectedTagNames.size());
                        updateContentBySelectedTags();
                    } else {
                        // 标签不存在（tagID为-1），不能添加
                        Log.w(TAG, "updateTagSelectionByName: tag not found (tagID=-1), tagName=" + tagName);
                        Toast.makeText(requireContext(), "标签不存在: " + tagName, Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "updateTagSelectionByName: failed to get tagID for tag: " + tagName, e);
                    Toast.makeText(requireContext(), "验证标签失败: " + tagName, Toast.LENGTH_SHORT).show();
                }
            }
        }
        
        // 如果处于选择模式，退出选择模式（标签选择会改变内容，需要重新选择）
        if (isSelectionMode) {
            toggleSelectionMode();
        }
        
        // 更新适配器显示（两个适配器都需要更新）
        if (tagsAdapter != null) {
            tagsAdapter.setSelectedTagNames(selectedTagNames);
        }
        if (tagsAdapterCollapsed != null) {
            tagsAdapterCollapsed.setSelectedTagNames(selectedTagNames);
        }
        
        // 统一更新菜单可见性（标签选择可能影响菜单显示）
        updateMenuVisibility();
    }
    
    /**
     * 清理 selectedTagNames 中的无效标签（tagID为-1的标签）
     * 在每次更新标签选择状态时调用，确保内存中只保留有效的标签
     */
    private void cleanInvalidTagsFromSelection() {
        if (linkDao == null || selectedTagNames.isEmpty()) {
            return;
        }
        
        Set<String> invalidTags = new HashSet<>();
        boolean needUpdateNoTag = false;
        
        // 检查每个标签是否存在
        for (String tagName : new HashSet<>(selectedTagNames)) {
            if (NO_TAG.equals(tagName)) {
                // NO_TAG 总是有效的，不需要检查数量（允许count为0）
                // 不做任何处理，保留 NO_TAG
            } else {
                // 检查普通标签是否存在
                try {
                    long tagId = linkDao.getTagIdByName(tagName);
                    if (tagId == -1) {
                        invalidTags.add(tagName);
                        Log.w(TAG, "cleanInvalidTagsFromSelection: found invalid tag (tagID=-1), will remove: " + tagName);
                    }
                } catch (Exception e) {
                    invalidTags.add(tagName);
                    Log.e(TAG, "cleanInvalidTagsFromSelection: failed to get tagID for tag: " + tagName, e);
                }
            }
        }
        
        // 移除无效标签
        if (!invalidTags.isEmpty()) {
            selectedTagNames.removeAll(invalidTags);
            Log.d(TAG, "cleanInvalidTagsFromSelection: removed " + invalidTags.size() + " invalid tags: " + invalidTags);
            
            // 更新 SharedPreferences
            SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            
            // 重新计算有效的标签集合
            Set<String> validTags = new HashSet<>(selectedTagNames);
            validTags.remove(NO_TAG);  // NO_TAG 单独处理
            
            editor.putStringSet(KEY_SELECTED_TAGS, validTags);
            if (needUpdateNoTag) {
                editor.putBoolean(KEY_NO_TAG_SELECTED, false);
            }
            editor.apply();
        }
    }
    
    // ========== 标签管理：数据加载（从TagsFragment合并）==========
    
    /**
     * 加载标签
     * 从数据库加载标签数据并更新UI
     */
    private void loadTags() {
        Log.d(TAG, "loadTags: start");
        if (tagsAdapter == null || tagsAdapterCollapsed == null) {
            Log.e(TAG, "loadTags: tagsAdapter is null, tagsAdapter=" + (tagsAdapter != null) + 
                    ", tagsAdapterCollapsed=" + (tagsAdapterCollapsed != null));
            return;
        }
        
        if (linkDao == null) {
            Log.e(TAG, "loadTags: linkDao is null!");
            return;
        }
        
        // 使用后台线程加载标签数据
        new Thread(() -> {
            try {
                Log.d(TAG, "loadTags: background thread start");
                
                // 统一检查Fragment状态和数据库状态（在后台线程开始时检查一次）
                // 注意：由于没有锁机制，后续操作前仍需要检查，但可以减少重复检查
                if (!isAdded() || linkDao == null) {
                    Log.w(TAG, "loadTags: Fragment not attached or linkDao is null, cancel loading");
                    return;
                }
                
                Map<String, Integer> tagsWithCount;
                AtomicInteger noTagCount = new AtomicInteger();
                List<TagsAdapter.TagItem> allTagItems = new ArrayList<>();
                
                try {
                    // 在数据库操作前再次检查（避免检查和使用之间的竞态条件）
                    if (!isAdded() || linkDao == null) {
                        Log.w(TAG, "loadTags: Fragment detached or linkDao closed before getTagsWithCount");
                        return;
                    }
                    
                    tagsWithCount = linkDao.getTagsWithCount();
                    Log.d(TAG, "loadTags: tag count=" + tagsWithCount.size());
                    
                    // 获取无标签的链接数量（需要访问数据库，可能抛出异常）
                    // 在数据库操作前再次检查
                    if (!isAdded() || linkDao == null) {
                        Log.w(TAG, "loadTags: Fragment detached or linkDao closed before getLinksWithoutTags");
                        return;
                    }
                    noTagCount.set(linkDao.getLinksWithoutTags().size());
                    
                    // 在后台线程中预先获取所有tagId，避免在UI线程中访问数据库
                    // 添加"无标签"选项（总是显示，即使数量为0）
                    allTagItems.add(new TagsAdapter.TagItem(-1, NO_TAG, noTagCount.get()));
                    
                    // 添加其他标签（在后台线程中获取tagId）
                    for (Map.Entry<String, Integer> entry : tagsWithCount.entrySet()) {
                        // 每次循环都检查状态
                        if (!isAdded() || linkDao == null) {
                            Log.w(TAG, "loadTags: Fragment detached or linkDao closed during tag processing");
                            break;
                        }
                        long tagId = linkDao.getTagIdByName(entry.getKey());
                        if (tagId != -1) {
                            allTagItems.add(new TagsAdapter.TagItem(tagId, entry.getKey(), entry.getValue()));
                        }
                    }
                } catch (IllegalStateException e) {
                    Log.w(TAG, "loadTags: database closed, cancel loading", e);
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "loadTags: unexpected exception in background thread", e);
                    return;
                }
            
                // 回到主线程更新UI
                try {
                    // 检查Fragment是否还attached
                    if (!isAdded() || getActivity() == null) {
                        Log.w(TAG, "loadTags: Fragment not attached or Activity is null, skip UI update");
                        return;
                    }
                    
                    Activity activity = getActivity();
                    if (activity != null && !activity.isFinishing()) {
                        // 使用final变量保存数据，避免在lambda中访问可能已关闭的linkDao
                        final List<TagsAdapter.TagItem> finalAllTagItems = allTagItems;
                        activity.runOnUiThread(() -> {
                            // 再次检查Fragment状态和adapter
                            if (!isAdded() || tagsAdapter == null) {
                                Log.w(TAG, "loadTags: Fragment state changed during UI update, skip update");
                                return;
                            }
                            
                            // 在排序模式下，显示所有标签在一个区域
                            if (isSortMode) {
                                if (tagsAdapter != null) {
                                    tagsAdapter.setTags(finalAllTagItems);
                                    tagsAdapter.setHighlightedTags(highlightedTags);
                                    tagsAdapter.setSelectedTagNames(selectedTagNames);
                                }
                                if (tagsAdapterCollapsed != null) {
                                    tagsAdapterCollapsed.setTags(new ArrayList<>());
                                }
                                if (tagsRecyclerViewCollapsed != null) {
                                    tagsRecyclerViewCollapsed.setVisibility(View.GONE);
                                }
                                if (expandMoreTagsButton != null) {
                                    expandMoreTagsButton.setVisibility(View.GONE);
                                }
                            } else {
                                // 非排序模式：分成固定区和折叠区
                                List<TagsAdapter.TagItem> fixedTags = new ArrayList<>();
                                List<TagsAdapter.TagItem> collapsedTags = new ArrayList<>();

                                // 恢复并验证已保存的选择到 selectedTagNames（不触发数据加载），
                                // 使后续按选中优先的逻辑能够直接使用权威的 selectedTagNames。
                                try {
                                    restoreSelections(false);
                                } catch (Exception e) {
                                    Log.e(TAG, "loadTags: restoreSelections failed", e);
                                }

                                // 1) 先把所有被选中的标签按 finalAllTagItems 顺序加入 fixedTags（避免重复）
                                for (TagsAdapter.TagItem item : finalAllTagItems) {
                                    String name = item.getName() != null ? item.getName().trim() : "";
                                    for (String sel : selectedTagNames) {
                                        if (sel != null && sel.trim().equals(name)) {
                                            if (!fixedTags.contains(item)) fixedTags.add(item);
                                            break;
                                        }
                                    }
                                }

                                // 2) 再按位置填充固定区，直到达到固定数（避免重复加入）
                                int fixedLimit = FIXED_TAGS_COUNT;
                                int addedCount = fixedTags.size();
                                for (int i = 0; i < finalAllTagItems.size() && addedCount < fixedLimit; i++) {
                                    TagsAdapter.TagItem item = finalAllTagItems.get(i);
                                    if (!fixedTags.contains(item)) {
                                        fixedTags.add(item);
                                        addedCount++;
                                        Log.i("FIXED_TAGS NAME", item.getName());
                                    }
                                }

                                // 3) 剩余的放入折叠区
                                for (TagsAdapter.TagItem item : finalAllTagItems) {
                                    if (!fixedTags.contains(item)) {
                                        collapsedTags.add(item);
                                    }
                                }

                                if (tagsAdapter != null) {
                                    tagsAdapter.setTags(fixedTags);
                                    tagsAdapter.setHighlightedTags(highlightedTags);
                                    tagsAdapter.setSelectedTagNames(selectedTagNames);
                                }
                                
                                if (tagsAdapterCollapsed != null) {
                                    tagsAdapterCollapsed.setTags(collapsedTags);
                                    tagsAdapterCollapsed.setHighlightedTags(highlightedTags);
                                    tagsAdapterCollapsed.setSelectedTagNames(selectedTagNames);
                                }
                                
                                // 如果有折叠标签，显示展开按钮；否则隐藏以减少空白
                                if (expandMoreTagsButton != null) {
                                    if (collapsedTags.size() > 0) {
                                        expandMoreTagsButton.setVisibility(View.VISIBLE);
                                        updateExpandMoreButtonState();
                                    } else {
                                        expandMoreTagsButton.setVisibility(View.GONE);
                                    }
                                }
                                
                                // 根据展开状态显示/隐藏折叠标签区
                                if (tagsRecyclerViewCollapsed != null) {
                                    tagsRecyclerViewCollapsed.setVisibility(
                                        isMoreTagsExpanded && collapsedTags.size() > 0 ? View.VISIBLE : View.GONE);
                                }
                            }
                            
                            // 标签加载完成后，检查是否需要显示展开按钮（排序模式下不显示）
                            if (tagsRecyclerView != null) {
                                tagsRecyclerView.post(() -> {
                                    // 排序模式下，按钮应该隐藏
                                    if (isSortMode) {
                                        if (toggleTagsButton != null) {
                                            toggleTagsButton.setVisibility(View.GONE);
                                        }
                                        return;
                                    }
                                    // 非排序模式下，根据内容高度决定是否显示
                                    int height = tagsRecyclerView.getHeight();
                                    float density = getResources().getDisplayMetrics().density;
                                    int collapsedHeightPx = (int) (COLLAPSED_HEIGHT_DP * density);
                                    if (toggleTagsButton != null) {
                                        toggleTagsButton.setVisibility(height > collapsedHeightPx ? View.VISIBLE : View.GONE);
                                    }
                                });
                            }

                            // 恢复状态后，需要同步链接列表，确保状态一致
                            // 避免标签显示选中但链接列表为空的问题
                            if (adapter != null && linkDao != null) {
                                if (selectedTagNames.isEmpty()) {
                                    // 如果没有选中任何标签，显示所有链接
                                    refreshLinksList();
                                } else {
                                    Log.d(TAG, "loadTags: selectedTagNames.size=" + selectedTagNames.size());
                                    // 打印选中的标签名称和对应的tagID
                                    for (String tagName : selectedTagNames) {
                                        if (NO_TAG.equals(tagName)) {
                                            Log.d(TAG, "loadTags: selected tag - name=" + tagName + ", tagID=-1 (NO_TAG)");
                                        } else {
                                            try {
                                                long tagId = linkDao.getTagIdByName(tagName);
                                                Log.d(TAG, "loadTags: selected tag - name=" + tagName + ", tagID=" + tagId);
                                            } catch (Exception e) {
                                                Log.e(TAG, "loadTags: failed to get tagID for tag: " + tagName, e);
                                            }
                                        }
                                    }
                                    // 如果有选中的标签，根据标签筛选内容
                                    updateContentBySelectedTags();
                                }
                            }
                        });  // 关闭runOnUiThread的lambda
                    } else {
                        Log.w(TAG, "loadTags: Activity is null or finished, skip UI update");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "loadTags: runOnUiThread exception", e);
                }
            } catch (Exception e) {
                Log.e(TAG, "loadTags: background thread exception", e);
            }
        }).start();
    }
    
    // ========== 链接筛选：数据获取与筛选（从TagsFragment合并）==========
    
    /**
     * 根据选中标签更新内容
     * 这是标签筛选的核心方法，根据选中的标签筛选链接并更新显示
     * 阶段2：统一数据加载逻辑
     */
    private void updateContentBySelectedTags() {
        Log.d(TAG, "updateContentBySelectedTags: start, selectedTagNames.size=" + selectedTagNames.size());
        try {
            if (linkDao == null) {
                Log.e(TAG, "updateContentBySelectedTags: linkDao is null!");
                return;
            }
            if (adapter == null) {
                Log.e(TAG, "updateContentBySelectedTags: adapter is null!");
                return;
            }
            
            // 如果没有选中任何标签，使用HomeFragment原有行为
            if (selectedTagNames.isEmpty()) {
                Log.d(TAG, "updateContentBySelectedTags: no tags selected, use original logic");
                // 使用原有逻辑加载所有链接
                List<LinkItem> pinnedLinks = linkDao.getPinnedLinks();
                Map<String, List<LinkItem>> groupedLinks = linkDao.getLinksGroupByDate();
                adapter.setPinnedLinks(pinnedLinks);
                adapter.setGroupedLinks(groupedLinks);
                adapter.notifyDataSetChanged();
                try {
                    requireActivity().setTitle("全部内容");
                } catch (Exception e) {
                    Log.e(TAG, "updateContentBySelectedTags: failed to set title", e);
                }
                return;
            }
            
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
            if (hasNoTagFilter) {
                links.addAll(linkDao.getLinksWithoutTags());
            }
            if (!tagNames.isEmpty()) {
                links.addAll(linkDao.getLinksByTags(tagNames));
            }
            
            Log.d(TAG, "updateContentBySelectedTags: filtering completed, links.size=" + links.size());

            // 更新标题
            updateTitle(tagNames, hasNoTagFilter);

            // 按日期分组显示
            Map<String, List<LinkItem>> groupedLinks = groupLinksByDate(links);
            Log.d(TAG, "updateContentBySelectedTags: grouping completed, groupedLinks.size=" + groupedLinks.size());
            
            // 计算置顶与筛选结果的交集
            List<LinkItem> pinnedOverlap = calculatePinnedOverlap(links);
            Log.d(TAG, "updateContentBySelectedTags: pinned overlap=" + pinnedOverlap.size());
            
            adapter.setPinnedLinks(pinnedOverlap);
            adapter.setGroupedLinks(groupedLinks);
            adapter.notifyDataSetChanged();

            // 保存选择状态
            saveSelections(tagNames, hasNoTagFilter);
            Log.d(TAG, "updateContentBySelectedTags: completed");
        } catch (Exception e) {
            Log.e(TAG, "updateContentBySelectedTags: exception occurred", e);
        }
    }
    
    /**
     * 更新标题
     * 根据选中的标签更新Activity标题
     */
    private void updateTitle(Set<String> tags, boolean includeNoTag) {
        try {
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
            Log.d(TAG, "updateTitle: " + title.toString());
        } catch (Exception e) {
            Log.e(TAG, "updateTitle: failed to update title", e);
        }
    }
    
    /**
     * 保存选择状态
     * 将标签选择状态保存到SharedPreferences
     */
    private void saveSelections(Set<String> tags, boolean includeNoTag) {
        SharedPreferences.Editor editor = requireContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit();
        
        // 保存标签选择
        editor.putStringSet(KEY_SELECTED_TAGS, tags);
        editor.putBoolean(KEY_NO_TAG_SELECTED, includeNoTag);
        editor.apply();
    }
    
    /**
     * 清除保存的选择状态
     */
    private void clearSavedSelections() {
        requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }
    
    /**
     * 同步恢复选择状态（仅恢复状态，不加载数据）
     * 从SharedPreferences恢复标签选择状态，但不触发数据加载
     * 用于onCreateView中先恢复状态，再根据状态加载数据
     */
    private void restoreSelectionsSync() {
        try {
            Log.d(TAG, "restoreSelectionsSync: start");
            SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            Set<String> savedTags = prefs.getStringSet(KEY_SELECTED_TAGS, new HashSet<>());
            boolean noTagSelected = prefs.getBoolean(KEY_NO_TAG_SELECTED, false);
            
            Log.d(TAG, "restoreSelectionsSync: savedTags.size=" + savedTags.size() + ", noTagSelected=" + noTagSelected);

            // 恢复选择状态
            selectedTagNames.clear();
            if (noTagSelected) {
                selectedTagNames.add(NO_TAG);
            }
            selectedTagNames.addAll(savedTags);
            
            Log.d(TAG, "restoreSelectionsSync: after restore selectedTagNames.size=" + selectedTagNames.size());
        } catch (Exception e) {
            Log.e(TAG, "restoreSelectionsSync: failed to restore selection state", e);
        }
    }
    
    /**
     * 恢复选择状态
     * 从SharedPreferences恢复标签选择状态，并更新UI和数据
     * @param shouldLoadData 是否加载数据（true：加载数据；false：仅更新UI状态）
     */
    private void restoreSelections(boolean shouldLoadData) {
        try {
            Log.d(TAG, "restoreSelections: start, shouldLoadData=" + shouldLoadData);
            SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            Set<String> savedTags = prefs.getStringSet(KEY_SELECTED_TAGS, new HashSet<>());
            boolean noTagSelected = prefs.getBoolean(KEY_NO_TAG_SELECTED, false);
            
            Log.d(TAG, "restoreSelections: savedTags.size=" + savedTags.size() + ", noTagSelected=" + noTagSelected);

            // 恢复选择状态
            selectedTagNames.clear();
            if (noTagSelected) {
                selectedTagNames.add(NO_TAG);
            }
            selectedTagNames.addAll(savedTags);
            
            Log.d(TAG, "restoreSelections: after restore selectedTagNames.size=" + selectedTagNames.size());
            
            // 更新适配器显示（两个适配器都需要更新）
            if (tagsAdapter != null) {
                tagsAdapter.setSelectedTagNames(selectedTagNames);
            }
            if (tagsAdapterCollapsed != null) {
                tagsAdapterCollapsed.setSelectedTagNames(selectedTagNames);
            }
            
            // 如果需要加载数据，根据选择状态加载
            if (shouldLoadData) {
                // 确保adapter和linkDao都已初始化
                if (adapter == null || linkDao == null) {
                    Log.w(TAG, "restoreSelections: adapter or linkDao not initialized, skip data loading");
                    return;
                }
                
                if (selectedTagNames.isEmpty()) {
                    // 如果没有选中任何标签，不需要重新加载数据
                    // 因为onViewCreated中已经加载了所有链接，这里只需要更新标签UI状态即可
                    Log.d(TAG, "restoreSelections: no tags selected, keep existing data (already loaded in onViewCreated)");
                    ///不调用refreshLinksList()，避免清空已加载的数据
                } else {
                    // 根据选中的标签筛选内容（覆盖之前的所有链接显示）
                    Log.d(TAG, "restoreSelections: filter content by selected tags, override previous all links display");
                    updateContentBySelectedTags();
                }
            }
            
            Log.d(TAG, "restoreSelections: completed");
        } catch (Exception e) {
            Log.e(TAG, "restoreSelections: failed to restore selection state", e);
            // 如果恢复失败且需要加载数据，确保至少显示所有链接
            if (shouldLoadData && selectedTagNames.isEmpty() && adapter != null && linkDao != null) {
                Log.d(TAG, "restoreSelections: restore failed, try to show all links");
                refreshLinksList();
            }
        }
    }
    
    /**
     * 恢复选择状态（默认加载数据）
     * 从SharedPreferences恢复标签选择状态
     */
    private void restoreSelections() {
        restoreSelections(true);
    }
    
    // ========== 标签管理：UI状态切换（从TagsFragment合并）==========
    
    /**
     * 切换更多标签展开/折叠
     */
    private void toggleMoreTagsExpansion() {
        isMoreTagsExpanded = !isMoreTagsExpanded;
        updateExpandMoreButtonState();
        
        // 显示/隐藏折叠标签区
        if (tagsAdapterCollapsed != null && tagsRecyclerViewCollapsed != null) {
            List<TagsAdapter.TagItem> collapsedTags = tagsAdapterCollapsed.getTags();
            boolean shouldShowCollapsed = isMoreTagsExpanded && collapsedTags.size() > 0;
            tagsRecyclerViewCollapsed.setVisibility(shouldShowCollapsed ? View.VISIBLE : View.GONE);
        }
        
        // 调整标签区域高度
        updateTagsScrollViewHeight();
    }
    
    /**
     * 更新展开更多按钮状态
     */
    private void updateExpandMoreButtonState() {
        if (textExpandMore != null && iconExpandMore != null) {
            if (isMoreTagsExpanded) {
                textExpandMore.setText("收起标签");
                iconExpandMore.setImageResource(R.drawable.ic_expand_less);
                iconExpandMore.setContentDescription("收起");
            } else {
                textExpandMore.setText("展开更多标签");
                iconExpandMore.setImageResource(R.drawable.ic_expand_more);
                iconExpandMore.setContentDescription("展开");
            }
        }
    }
    
    /**
     * 更新标签滚动区域的高度
     */
    private void updateTagsScrollViewHeight() {
        if (tagsScrollView != null) {
            ViewGroup.LayoutParams params = tagsScrollView.getLayoutParams();
            float density = getResources().getDisplayMetrics().density;
            
            // 如果展开更多标签状态或排序模式，使用相同的高度计算逻辑
            if (isMoreTagsExpanded || isSortMode) {
                // 展开更多标签或排序模式下，设置最大高度，允许滚动显示所有标签
                // 计算可用高度：屏幕高度 - 搜索框高度 - 状态栏/导航栏 - 底部边距
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                
                // 获取搜索框高度（如果存在）
                int searchBoxHeight = 0;
                if (searchEditText != null && searchEditText.getVisibility() == View.VISIBLE) {
                    searchEditText.measure(
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                    searchBoxHeight = searchEditText.getMeasuredHeight();
                }
                
                // 预留一些边距（底部按钮高度 + 一些padding + 安全边距）
                // 考虑：状态栏、导航栏、底部按钮、链接列表的顶部边距
                int statusBarHeight = 0;
                int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
                if (resourceId > 0) {
                    statusBarHeight = getResources().getDimensionPixelSize(resourceId);
                }
                
                int bottomMargin = (int) (64 * density);  // 底部按钮、边距和安全区域
                int availableHeight = screenHeight - searchBoxHeight - statusBarHeight - bottomMargin;
                
                // 使用可用高度的60%或固定400dp，取较小值
                int maxHeightByScreen = (int) (availableHeight * 0.6f);
                int maxHeightByDp = (int) (SORT_MODE_MAX_HEIGHT_DP * density);
                int maxHeightPx = Math.min(maxHeightByScreen, maxHeightByDp);
                
                // 先测量内容高度
                tagsScrollView.measure(
                    View.MeasureSpec.makeMeasureSpec(params.width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                int measuredHeight = tagsScrollView.getMeasuredHeight();
                
                // 如果测量高度小于最大高度，使用 wrap_content；否则使用最大高度（允许滚动）
                params.height = measuredHeight <= maxHeightPx 
                    ? ViewGroup.LayoutParams.WRAP_CONTENT 
                    : maxHeightPx;
                
                String mode = isSortMode ? "排序模式" : "展开更多标签模式";
                Log.d(TAG, "updateTagsScrollViewHeight: " + mode + ", screenHeight=" + screenHeight 
                    + ", searchBoxHeight=" + searchBoxHeight + ", availableHeight=" + availableHeight
                    + ", measuredHeight=" + measuredHeight + ", maxHeightPx=" + maxHeightPx 
                    + ", 设置高度=" + params.height);
            } else {
                // 非排序模式下，限制最大高度为 120dp
                int maxHeightPx = (int) (COLLAPSED_HEIGHT_DP * density);
                // 先测量内容高度
                tagsScrollView.measure(
                    View.MeasureSpec.makeMeasureSpec(params.width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                int measuredHeight = tagsScrollView.getMeasuredHeight();
                // 如果测量高度小于最大高度，使用 wrap_content；否则使用最大高度
                params.height = measuredHeight <= maxHeightPx 
                    ? ViewGroup.LayoutParams.WRAP_CONTENT 
                    : maxHeightPx;
            }
            tagsScrollView.setLayoutParams(params);
        }
    }
    
    /**
     * 切换标签区域展开/折叠
     */
    private void toggleTagsExpansion() {
        isTagsExpanded = !isTagsExpanded;
        
        if (tagsScrollView == null || arrowIndicator == null) return;
        
        ViewGroup.LayoutParams params = tagsScrollView.getLayoutParams();
        
        if (isTagsExpanded) {
            // 展开状态 - 设置为自适应高度
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
        if (toggleTagsButton != null) {
            toggleTagsButton.bringToFront();
        }
    }
    
    /**
     * 检查标签高度和更新按钮可见性
     */
    private void checkTagsVisibility() {
        if (tagsRecyclerView == null || toggleTagsButton == null) return;
        
        // 排序模式下，隐藏按钮
        if (isSortMode) {
            toggleTagsButton.setVisibility(View.GONE);
            return;
        }
        
        // 测量高度
        int height = tagsRecyclerView.getHeight();
        // 计算折叠高度（像素）
        float density = getResources().getDisplayMetrics().density;
        int collapsedHeightPx = (int) (COLLAPSED_HEIGHT_DP * density);
        
        // 如果内容高度大于折叠高度，显示按钮，否则隐藏
        toggleTagsButton.setVisibility(height > collapsedHeightPx ? View.VISIBLE : View.GONE);
        
        // 如果按钮可见，确保其状态与 isTagsExpanded 一致
        if (toggleTagsButton.getVisibility() == View.VISIBLE && arrowIndicator != null) {
            arrowIndicator.setImageResource(isTagsExpanded ? 
                R.drawable.ic_expand_less : R.drawable.ic_expand_more);
        }
    }
    
    /**
     * 加载高亮标签设置
     */
    private void loadHighlightedTags() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        highlightedTags = new HashSet<>(prefs.getStringSet(PREF_HIGHLIGHTED_TAGS, new HashSet<>()));
    }
    
    /**
     * 切换标签高亮
     */
    private void toggleTagHighlight(String tagName) {
        // 如果标签已经高亮，则取消高亮
        if (highlightedTags.contains(tagName)) {
            highlightedTags.remove(tagName);
        } else {
            // 否则添加高亮
            highlightedTags.add(tagName);
        }
        
        // 保存到SharedPreferences
        SharedPreferences.Editor editor = requireContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit();
        editor.putStringSet(PREF_HIGHLIGHTED_TAGS, highlightedTags);
        editor.apply();
        
        // 更新适配器显示
        if (tagsAdapter != null) {
            tagsAdapter.setHighlightedTags(highlightedTags);
        }
        if (tagsAdapterCollapsed != null) {
            tagsAdapterCollapsed.setHighlightedTags(highlightedTags);
        }
        
        // 显示提示
        String message = highlightedTags.contains(tagName) ? "已高亮标签：" + tagName : "已取消高亮：" + tagName;
        Snackbar.make(requireView(), message, Snackbar.LENGTH_SHORT).show();
    }
    
    /**
     * 保存标签排序到数据库
     */
    private void saveTagOrderToDatabase() {
        List<Long> orderedTagIds = new ArrayList<>();
        
        // 在排序模式下，所有标签都在固定区
        // 在非排序模式下，合并固定区和折叠区的标签
        List<TagsAdapter.TagItem> allTags = new ArrayList<>();
        if (tagsAdapter != null) {
            allTags.addAll(tagsAdapter.getTags());
        }
        if (tagsAdapterCollapsed != null && !isSortMode) {
            allTags.addAll(tagsAdapterCollapsed.getTags());
        }
        
        for (TagsAdapter.TagItem tag : allTags) {
            orderedTagIds.add(tag.getId());
        }
        
        linkDao.saveTagOrder(orderedTagIds);
    }
    
    // ========== 标签管理：对话框显示（从TagsFragment合并）==========
    
    /**
     * 显示标签选项对话框
     */
    private void showTagOptionsDialog(String tag) {
        String[] options = {"删除标签", "删除标签及所有关联链接", "发布到网站", "切换高亮"};
        
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
    
    /**
     * 确认删除标签
     */
    private void confirmDeleteTag(String tag, boolean isCascading) {
        try {
            new AlertDialog.Builder(requireContext())
                .setTitle("确认删除")
                .setMessage("确定要删除标签 \"" + tag + "\" 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    try {
                        Log.d("HomeFragment", "Start deleting tag: " + tag);
                        
                        // 从数据库中删除标签
                        if (!isCascading) {
                            linkDao.deleteTag(tag);
                        } else { // 级联删除
                            linkDao.deleteTagWithLinks(tag);
                        }
                        Log.d("HomeFragment", "Tag deleted from database");
                        
                        // 从当前选中的标签集合中移除
                        selectedTagNames.remove(tag);
                        Log.d("HomeFragment", "Tag removed from selected set");
                        
                        // 重新加载标签
                        loadTags();
                        Log.d("HomeFragment", "Tag list reloaded");
                        
                        // 更新链接列表
                        if (selectedTagNames.isEmpty()) {
                            refreshLinksList();  // 使用原有方法
                        } else {
                            updateContentBySelectedTags();
                        }
                        
                        // 显示成功提示
                        Toast.makeText(requireContext(), 
                            "标签已删除", 
                            Toast.LENGTH_SHORT).show();
                        
                    } catch (Exception e) {
                        Log.e("HomeFragment", "Error deleting tag", e);
                        Toast.makeText(requireContext(), 
                            "删除标签失败: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
        } catch (Exception e) {
            Log.e("HomeFragment", "Error showing confirmation dialog", e);
            Toast.makeText(requireContext(), 
                "操作失败: " + e.getMessage(), 
                Toast.LENGTH_SHORT).show();
        }
        
        // 检查标签可见性
        if (tagsRecyclerView != null) {
            tagsRecyclerView.post(this::checkTagsVisibility);
        }
    }
    
    /**
     * 发布标签到网站
     * 注意：此功能在阶段0中已标记为待迁移到Sync模块，这里保留基本实现
     */
    private void publishTagToWebsite(String tag) {
        Log.d("HomeFragment", "Start publishing tag to website: " + tag);
        // TODO: 此功能将在后续阶段迁移到Sync模块
        Snackbar.make(requireView(), "发布功能待迁移到Sync模块", Snackbar.LENGTH_SHORT).show();
    }
    
    /**
     * 显示添加标签对话框
     */
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
}