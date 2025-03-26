package person.notfresh.readingshare.adapter;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.LabeledIntent;
import android.net.Uri;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.PopupMenu;
import android.widget.Toast;
import android.view.Menu;
import android.view.MenuItem;
import android.os.Handler;
import android.os.Looper;
import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.io.File;
import java.util.Arrays;
import android.content.ComponentName;
import java.util.stream.Collectors;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.model.LinkItem;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.flexbox.FlexboxLayout;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.util.ExportUtil;
import person.notfresh.readingshare.util.BilibiliUrlConverter;
import java.io.IOException;
import person.notfresh.readingshare.util.AppUtils;
import person.notfresh.readingshare.WebViewActivity;
import person.notfresh.readingshare.util.CrawlUtil;
import person.notfresh.readingshare.util.RecentTagsManager;
import person.notfresh.readingshare.util.SwipeActionsHelper;

public class LinksAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_PINNED_HEADER = -1;
    private static final int TYPE_DATE_HEADER = 0;
    private static final int TYPE_LINK_ITEM = 1;
    
    private List<Object> items = new ArrayList<>();
    private List<Object> originalItems = new ArrayList<>();  // 存储原始数据
    private OnLinkActionListener listener; // 具体表示哪个Fragment
    private LinkDao linkDao;
    private static Context context;  // 添加 context 引用
    private Set<LinkItem> selectedItems = new HashSet<>();
    private boolean isSelectionMode = false;
    private List<LinkItem> links = new ArrayList<>();
    private List<LinkItem> pinnedLinks = new ArrayList<>();
    private Map<String, List<LinkItem>> groupedLinks = new TreeMap<>(Collections.reverseOrder());
    private SwipeActionsHelper swipeActionsHelper;

    public interface OnLinkActionListener {
        void onDeleteLink(LinkItem link);
        void onUpdateLink(LinkItem oldLink, String newTitle);
        void addTagToLink(LinkItem item, String tag);
        void addTagsToLink(LinkItem item, List<String> tags);
        void updateLinkTags(LinkItem item);
        void onEnterSelectionMode();  // 添加新的回调方法
        void onPinStatusChanged();
    }

    public LinksAdapter(Context context) {
        this.context = context;
        this.linkDao = new LinkDao(context);
        this.linkDao.open();
        this.swipeActionsHelper = new SwipeActionsHelper(this);
    }

    public void setOnLinkActionListener(OnLinkActionListener listener) {
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof String ? TYPE_DATE_HEADER : TYPE_LINK_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_DATE_HEADER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_date_header, parent, false);
            return new DateHeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_link, parent, false);
            return new LinkViewHolder(view, this);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof DateHeaderViewHolder) {
            ((DateHeaderViewHolder) holder).bind((String) items.get(position));
        } else if (holder instanceof LinkViewHolder) {
            LinkItem item = (LinkItem) items.get(position);
            ((LinkViewHolder) holder).bind(item);
            if (item.isPinned()) {
                holder.itemView.setBackgroundResource(R.drawable.pinned_item_background);
            }else{
                holder.itemView.setBackgroundResource(R.drawable.normal_background);
            }
            if (selectedItems.contains(item)){
                holder.itemView.setBackgroundResource(R.drawable.selected_background);
            }
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void setGroupedLinks(Map<String, List<LinkItem>> groupedLinks) {
        items.clear();
        originalItems.clear();
        this.groupedLinks = groupedLinks;  // 保存分组数据
        
        // 首先添加置顶链接区域
        if (!pinnedLinks.isEmpty()) {
            items.add("置顶");  // 添加置顶区域的标题
            items.addAll(pinnedLinks);
        }
        
        // 然后添加按日期分组的普通链接
        for (Map.Entry<String, List<LinkItem>> entry : groupedLinks.entrySet()) {
            items.add(entry.getKey());
            // 过滤掉已经在置顶区域显示的链接
            List<LinkItem> normalLinks = entry.getValue().stream()
                    .filter(link -> !pinnedLinks.contains(link))
                    .collect(Collectors.toList());
            items.addAll(normalLinks);
        }
        
        // 保存原始数据
        originalItems.addAll(items);
        notifyDataSetChanged();
        
        Log.d("LinksAdapter", "设置数据: 置顶链接数=" + pinnedLinks.size() + 
                ", 总项目数=" + items.size());
    }

    public void addTagToLink(LinkItem item, String tagName) {
        item.addTag(tagName);
        notifyDataSetChanged();
        linkDao.updateLinkTags(item);
    }

    //@mark.2
    public void addTagsToLink(LinkItem item, List<String> tagNames) {
        for(String tagName: tagNames){
            item.addTag(tagName);
        }
        notifyDataSetChanged();
        linkDao.updateLinkTags(item);
        
        // 保存最近使用的标签
        RecentTagsManager.addRecentTags(context, tagNames);
    }

    public void updateLinkTags(LinkItem item) {
        linkDao.updateLinkTags(item);
    }

    // 提取真实URL的辅助方法
    String extractRealUrl(String text) {
        int urlStart = text.indexOf("http");
        if (urlStart != -1) {
            String url = text.substring(urlStart);
            int spaceIndex = url.indexOf(" ");
            if (spaceIndex != -1) {
                url = url.substring(0, spaceIndex);
            }
            return url;
        }
        return text;
    }

    // 检查是否有应用可以处理该Intent
    boolean isIntentAvailable(Context context, Intent intent) {
        return !context.getPackageManager()
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .isEmpty();
    }

    public void showActionDialog(View view, LinkItem item, int position) {
        Context wrapper = new ContextThemeWrapper(context, R.style.PopupMenuTheme);
        PopupMenu popup = new PopupMenu(wrapper, view);
        popup.getMenu().add(0, 1, 0, "编辑标题");
        popup.getMenu().add(0, 2, 0, "删除");
        popup.getMenu().add(0, 3, 0, "分享单条");
        popup.getMenu().add(0, 4, 0, "切换置顶");
        popup.getMenu().add(0, 5, 0, "多选模式");
        popup.getMenu().add(0, 6, 0, "获取摘要");
        
        popup.setOnMenuItemClickListener(menuItem -> {
            switch (menuItem.getItemId()) {
                case 1:
                    showEditTitleDialog(view, item);
                    return true;
                case 2:
                    if (listener != null) {
                        listener.onDeleteLink(item);
                    }
                    return true;
                case 3:
                    shareAsText(item);
                    return true;
                case 4:
                    Log.d("LinksAdapter", "切换置顶被点击, linkId: " + item.getId() + ", 当前置顶状态: " + item.isPinned());
                    linkDao.togglePinStatus(item.getId());
                    // 通知 Fragment 刷新数据
                    if (listener != null) {
                        Log.d("LinksAdapter", "调用 onPinStatusChanged");
                        listener.onPinStatusChanged();
                    }
                    return true;
                case 5:
                    // 进入多选模式并选中当前项
                    Log.d("LinksAdapter", "进入多选模式");
                    if (listener != null) {
                        listener.onEnterSelectionMode();
                        // Log.d("LinksAdapter", "添加当前项到选中集合: " + item.getTitle());
                        //selectedItems.add(item);
                        Log.d("LinksAdapter", "刷新位置: " + position);
                        //notifyItemChanged(position);
                    }
                    return true;
                case 6:
                    // 获取摘要
                    Log.d("LinksAdapter", "获取摘要");
                    // 防止重复点击
                    popup.getMenu().findItem(6).setEnabled(false);
                    
                    // 显示加载对话框
                    ProgressDialog progressDialog = new ProgressDialog(view.getContext());
                    progressDialog.setMessage("正在获取摘要...");
                    progressDialog.setCancelable(true);
                    progressDialog.show();
                    
                    // 异步获取摘要
                    new Thread(() -> {
                        try {
                            String summary = CrawlUtil.getUrlSummary(item.getUrl(), 60);
                            // 在主线程更新UI
                            new Handler(Looper.getMainLooper()).post(() -> {
                                progressDialog.dismiss();
                                if (summary != null && !summary.isEmpty()) {
                                    item.setSummary(summary);
                                    linkDao.updateSummary(item.getId(), summary);
                                    notifyItemChanged(position);
                                    Toast.makeText(view.getContext(), "摘要获取成功", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(view.getContext(), "获取摘要失败：网页内容无法解析", Toast.LENGTH_SHORT).show();
                                }
                                // 重新启用菜单项
                                popup.getMenu().findItem(6).setEnabled(true);
                            });
                        } catch (Exception e) {
                            new Handler(Looper.getMainLooper()).post(() -> {
                                progressDialog.dismiss();
                                String errorMsg = e.getMessage();
                                // 处理特定错误情况
                                if (errorMsg != null && errorMsg.contains("302")) {
                                    errorMsg = "该链接可能需要登录或无法直接访问";
                                }
                                Log.e("LinksAdapter", "获取摘要失败: " + errorMsg);
                                Toast.makeText(view.getContext(), 
                                    "获取摘要失败：" + errorMsg, 
                                    Toast.LENGTH_SHORT).show();
                                // 重新启用菜单项
                                popup.getMenu().findItem(6).setEnabled(true);
                            });
                        }
                    }).start();
                    notifyItemChanged(position);
                    return true;
                default:
                    return false;
            }
        });
        
        popup.show();
    }

    private void showEditTitleDialog(View view, LinkItem item) {
        EditText input = new EditText(view.getContext());
        input.setText(item.getTitle());
        
        new AlertDialog.Builder(view.getContext())
                .setTitle("编辑标题")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String newTitle = input.getText().toString();
                    if (!newTitle.isEmpty() && listener != null) {
                        listener.onUpdateLink(item, newTitle);
                        item.setTitle(newTitle);
                        notifyDataSetChanged();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        if (linkDao != null) {
            linkDao.close();
        }
    }

    public void toggleSelectionMode() {
        Log.d("LinksAdapter", "Toggling selection mode. Current: " + isSelectionMode);  // 添加日志
        isSelectionMode = !isSelectionMode; //@mark.4
        if (!isSelectionMode) {
            selectedItems.clear();
        }
        notifyDataSetChanged();
        Log.d("LinksAdapter", "Selection mode toggled to: " + isSelectionMode);  // 添加日志
    }

    public void toggleItemSelection(LinkItem item) { //@mark.5
        if (selectedItems.contains(item)) {
            selectedItems.remove(item);
        } else {
            selectedItems.add(item);
        }
        notifyDataSetChanged();
    }

    public Set<LinkItem> getSelectedItems() {
        return new HashSet<>(selectedItems);
    }

    public void setLinks(List<LinkItem> newLinks) {
        // 清除现有的分组数据
        items.clear();
        
        // 对新的链接列表进行分组
        if (newLinks != null && !newLinks.isEmpty()) {
            // 按日期分组
            Map<String, List<LinkItem>> groups = new TreeMap<>(Collections.reverseOrder());
            for (LinkItem item : newLinks) {
                String date = formatDate(item.getTimestamp());
                groups.computeIfAbsent(date, k -> new ArrayList<>()).add(item);
            }
            
            // 转换为展平的列表
            for (Map.Entry<String, List<LinkItem>> entry : groups.entrySet()) {
                items.add(entry.getKey());
                items.addAll(entry.getValue());
            }
        }
        
        // 同时更新 links 列表（用于 getLinks 方法）
        this.links = new ArrayList<>(newLinks);
        
        // 通知适配器数据已更新
        notifyDataSetChanged();
    }

    private String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    // 添加获取当前链接列表的方法
    public List<LinkItem> getLinks() {
        List<LinkItem> currentLinks = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof LinkItem) {
                currentLinks.add((LinkItem) item);
            }
        }
        return currentLinks;
    }

    private void shareAsText(LinkItem item) {
        StringBuilder shareText = new StringBuilder();
        shareText.append(item.getTitle()).append("\n");
        if (item.getRemark() != null && !item.getRemark().isEmpty()) {
            shareText.append(item.getRemark()).append("\n");
        }
        shareText.append(item.getUrl());
        
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
        
        // 创建选择器并排除自己的应用
        Intent chooserIntent = Intent.createChooser(shareIntent, "分享到");
        String myPackageName = context.getPackageName();
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        chooserIntent.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, 
            new ComponentName[]{new ComponentName(myPackageName, myPackageName + ".MainActivity")});
        
        context.startActivity(chooserIntent);
    }

    private void shareAsFile(LinkItem item, boolean isJson) {
        try {
            List<LinkItem> singleItemList = Collections.singletonList(item);
            String filePath = isJson ? 
                ExportUtil.exportToJson(context, singleItemList) : 
                ExportUtil.exportToCsv(context, singleItemList);
            
            File file = new File(filePath);
            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                context, context.getPackageName() + ".provider", file);
                
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("*/*");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            // 创建选择器并排除自己的应用
            Intent chooserIntent = Intent.createChooser(shareIntent, "分享文件");
            String myPackageName = context.getPackageName();
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            chooserIntent.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, 
                new ComponentName[]{new ComponentName(myPackageName, myPackageName + ".MainActivity")});
            
            context.startActivity(chooserIntent);
            
        } catch (Exception e) {
            Toast.makeText(context, "分享失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showDeleteConfirmDialog(LinkItem item) {
        new AlertDialog.Builder(context)
                .setTitle("删除确认")
                .setMessage("确定要删除这个链接吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    if (listener != null) {
                        listener.onDeleteLink(item);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    public void selectItem(LinkItem item) {
        if (!selectedItems.contains(item)) {
            selectedItems.add(item);
        }
    }

    // 添加搜索方法
    public void filter(String query) {
        query = query.toLowerCase().trim();
        items.clear();
        
        if (query.isEmpty()) {
            // 如果搜索框为空，显示所有数据
            items.addAll(originalItems);
            notifyDataSetChanged();
            return;
        }

        Map<String, List<LinkItem>> filteredGroups = new TreeMap<>(Collections.reverseOrder());
        
        // 遍历原始数据进行过滤
        for (Object item : originalItems) {
            if (item instanceof String) {
                continue;  // 跳过日期标题
            }
            
            LinkItem linkItem = (LinkItem) item;
            boolean matchesTitle = linkItem.getTitle().toLowerCase().contains(query);
            boolean matchesTags = false;
            
            // 检查标签
            for (String tag : linkItem.getTags()) {
                if (tag.toLowerCase().contains(query)) {
                    matchesTags = true;
                    break;
                }
            }
            
            // 如果标题或标签匹配，添加到过滤结果中
            if (matchesTitle || matchesTags) {
                String date = formatDate(linkItem.getTimestamp());
                filteredGroups.computeIfAbsent(date, k -> new ArrayList<>()).add(linkItem);
            }
        }
        
        // 将过滤后的结果转换为展平的列表
        for (Map.Entry<String, List<LinkItem>> entry : filteredGroups.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                items.add(entry.getKey());  // 添加日期标题
                items.addAll(entry.getValue());  // 添加该日期下的链接
            }
        }
        
            notifyDataSetChanged();
    }

    private static void handleBilibiliLink(String shortUrl) {
        new Thread(() -> {
            try {
                String fullUrl = BilibiliUrlConverter.getRedirectedUrl(shortUrl);
                Log.d("BilibiliLink", "Full URL: " + fullUrl);
                String schemeUrl = BilibiliUrlConverter.convertToBilibiliScheme(fullUrl);
                Log.d("BilibiliLink", "Scheme URL: " + schemeUrl);
                ((AppCompatActivity) context).runOnUiThread(() -> openBilibiliApp(schemeUrl, fullUrl));
            } catch (IOException e) {
                Log.e("BilibiliLink", "Error: " + e.getMessage());
            }
        }).start();
    }

    private static void openBilibiliApp(String schemeUrl, String fallbackUrl) {
//        Context appContext = context.getApplicationContext(); // the global method
//        if (AppUtils.isAppInstalled(appContext, "tv.danmaku.bili")) { //TODO There is some wrong with this method
//            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(schemeUrl));
//            intent.setPackage("tv.danmaku.bili"); // Bilibili 的包名
//            context.startActivity(intent);
//            Log.d("BilibiliLink", "Bilibili app is installed, opening with app.");
//        } else {
//            // 如果没有安装 Bilibili 应用，打开浏览器
//            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl));
//            context.startActivity(browserIntent);
//            Log.d("BilibiliLink", "Bilibili app is not installed, opening with browser.");
//        }
        try{
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(schemeUrl));
            intent.setPackage("tv.danmaku.bili"); // Bilibili 的包名
            context.startActivity(intent);
        }catch (Exception e){
            //如果没有安装 Bilibili 应用，打开浏览器
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl));
            context.startActivity(browserIntent);
            Log.d("BilibiliLink", "Bilibili app is not installed, opening with browser.");
        }
    }

    private static void handleWereadLink(String url) {
        try{
//            url = "weread://reader?bookId=32d32c30813ab96d9g011e41";
//
//            url = "https://weread.qq.com/book-detail?type=1&senderVid=2852293&v=32d32c30813ab96d9g011e41&wtheme=white&wfrom=app&wvid=2852293&scene=bottomSheetShare";
//            url = "weread://reader?bookId=32d32c30813ab96d9g011e41";
//            url = "https://weread.qq.com/book-detail?v=1e932960813ab7d48g0115ff"; //无用
//            url = "weread://share?bookId=1e932960813ab7d48g0115ff";
//            url = "weread://reader?bookId=1e932960813ab7d48g0115ff&type=1&senderVid=2852293&wtheme=white&wfrom=app&wvid=2852293&scene=bottomSheetShare";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
//            intent.setPackage("com.tencent.weread"); // 微信读书的包名
            context.startActivity(intent);
        }catch (Exception e){
            //如果没有安装 对应的应用，用默认浏览器打开
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            context.startActivity(browserIntent);
            Log.d("WereadLink", "Weread app is not installed, opening with browser.");
        }
    }

    private String getItemUrl(int position) {
        if (items.get(position) instanceof LinkItem) {
            return ((LinkItem) items.get(position)).getUrl();
        }
        return null;
    }

    public static class LinkViewHolder extends RecyclerView.ViewHolder {
        TextView titleText;
        TextView urlText;
        TextView summaryText;
        TextView showMoreText;
        TextView clickCountText;  // 阅读次数显示
        FlexboxLayout tagContainer;
        Button addTagButton;
        private final LinksAdapter adapter;

        LinkViewHolder(View view, LinksAdapter adapter) {
            super(view);
            this.adapter = adapter;
            titleText = view.findViewById(R.id.text_title);
            urlText = view.findViewById(R.id.text_url);
            summaryText = view.findViewById(R.id.text_summary);
            showMoreText = view.findViewById(R.id.text_show_more);
            tagContainer = view.findViewById(R.id.tag_container);
            addTagButton = view.findViewById(R.id.btn_add_tag);
            clickCountText = view.findViewById(R.id.click_count_text);  // 绑定阅读次数控件
        }

        void bind(LinkItem item) {
            Log.i("LinkViewHolder", item.getTitle() + "调用bind方法");
            titleText.setText(item.getTitle());
            urlText.setText(formatUrlForDisplay(item.getUrl()));
            tagContainer.removeAllViews();
            
            // 添加标签
            for (String tag : item.getTags()) {
                addTagView(tag, item);
            }

            // 添加标签按钮
            addTagButton.setOnClickListener(v -> showAddTagDialog(v.getContext(), item));

            // 显示阅读次数
            clickCountText.setText("阅读 " + item.getClickCount() + " 次");  // 设置阅读次数

            // 添加选择模式的视觉反馈
            if (adapter.isSelectionMode) {
//                itemView.setBackgroundResource(
//                    adapter.selectedItems.contains(item) ?
//                    R.drawable.selected_background :
//                    R.drawable.normal_background
//                ); TODO 未解之谜，放在这里无法生效
                String title = item.getTitle();
                String color = adapter.selectedItems.contains(item) ?
                        "Selected" :"Normal";
                Log.i("LinkViewHolder", "当前项目" + title + "设置背景色为" + color);

                // 在选择模式下的点击处理
                itemView.setOnClickListener(v -> {
                    Log.d("LinkViewHolder", "选择模式点击 - 项目: " + item.getTitle());
                    if (adapter.selectedItems.contains(item)) {
                        Log.d("LinkViewHolder", "移除选中项目" + title);
                        adapter.selectedItems.remove(item);
                        itemView.setBackgroundResource(R.drawable.normal_background);
                    } else {
                        Log.d("LinkViewHolder", "添加选中项目" + title);
                        adapter.selectedItems.add(item);
                        itemView.setBackgroundResource(R.drawable.selected_background);
                    }
                });
                
            } else {
                // 正常模式下的点击和长按处理
                itemView.setOnClickListener(v -> {
                    try {
                        String url = adapter.extractRealUrl(item.getUrl());
                        // 创建基础 Intent
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                        if (url.contains("b23.tv")) {
                            handleBilibiliLink(url); //@Def Line408
                        } else if (url.contains("weread.qq.com")) {
                            handleWereadLink(url);
                        } else {
                            // 使用内置 WebView 打开普通链接
                            adapter.openLink(v.getContext(), url, getAdapterPosition());
                        }
                    } catch (Exception e) {
                        Snackbar.make(v, "无法打开此链接: " + e.getMessage(), 
                                Snackbar.LENGTH_LONG).show();
                    }
                });
            }

            // 添加长按处理
            itemView.setOnLongClickListener(v -> {
                adapter.showActionDialog(v, item, getAdapterPosition());
                return true;
            });

            // 处理摘要显示
            String summary = item.getSummary();
            if (summary != null && !summary.isEmpty()) {
                summaryText.setVisibility(View.VISIBLE);
                showMoreText.setVisibility(View.VISIBLE);
                
                // 默认只显示1行
                summaryText.setMaxLines(1);
                summaryText.setText(summary);
                
                showMoreText.setText("显示更多");
                showMoreText.setOnClickListener(v -> {
                    if (summaryText.getMaxLines() == 1) {
                        summaryText.setMaxLines(Integer.MAX_VALUE);
                        showMoreText.setText("收起");
                    } else {
                        summaryText.setMaxLines(1);
                        showMoreText.setText("显示更多");
                    }
                });
            } else {
                summaryText.setVisibility(View.GONE);
                showMoreText.setVisibility(View.GONE);
            }
        }

        private void addTagView(String tag, LinkItem item) {
            TextView tagView = (TextView) LayoutInflater.from(itemView.getContext())
                    .inflate(R.layout.item_tag, tagContainer, false);
            tagView.setText(tag);
            tagView.setOnClickListener(v -> showTagOptionsDialog(v.getContext(), tag, item));
            tagContainer.addView(tagView);
        }

        private void showAddTagDialog(Context context, LinkItem item) {
            // 创建一个自定义布局，包含输入框和最近标签
            View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_tag, null);
            EditText input = dialogView.findViewById(R.id.edit_tag_input);
            FlexboxLayout recentTagsContainer = dialogView.findViewById(R.id.recent_tags_container);
            
            // 获取并显示最近标签
            List<String> recentTags = RecentTagsManager.getRecentTags(context);
            if (!recentTags.isEmpty()) {
                TextView recentTagsLabel = dialogView.findViewById(R.id.text_recent_tags_label);
                recentTagsLabel.setVisibility(View.VISIBLE);
                
                for (String tag : recentTags) {
                    TextView tagView = (TextView) LayoutInflater.from(context)
                            .inflate(R.layout.item_recent_tag, recentTagsContainer, false);
                    tagView.setText(tag);
                    tagView.setOnClickListener(v -> {
                        String currentText = input.getText().toString().trim();
                        // 如果输入框不为空且不以逗号结尾，添加逗号分隔符
                        if (!currentText.isEmpty() && !currentText.endsWith(",") && !currentText.endsWith("，")) {
                            input.setText(currentText + "，" + tag);
                        } else {
                            // 如果为空或以逗号结尾，直接添加标签
                            input.setText(currentText + tag);
                        }
                        // 将光标移到末尾
                        input.setSelection(input.getText().length());
                    });
                    recentTagsContainer.addView(tagView);
                }
            }

            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setTitle("添加标签")
                    .setView(dialogView)
                    .setPositiveButton("确定", (dialogInterface, which) -> {
                        String tagName = input.getText().toString().trim();
                        String[] tags = tagName.split("[,，]");
                        List<String> tagList = new ArrayList<>();
                        for (String tag : tags) {
                            String trimmedTag = tag.trim();
                            if (!trimmedTag.isEmpty()) {
                                tagList.add(trimmedTag);
                            }
                        }
                        
                        // 记录最近使用的标签
                        if (!tagList.isEmpty()) {
                            RecentTagsManager.addRecentTags(context, tagList);
                        }
                        
                        adapter.addTagsToLink(item, tagList); //@mark.1
                        // adapter.listener.addTagToLink(item, ); //@mark.3

                    })
                    .setNegativeButton("取消", null)
                    .create();
            
            dialog.show();
        }

        private void showTagOptionsDialog(Context context, String tag, LinkItem item) {
            new AlertDialog.Builder(context)
                    .setItems(new String[]{"删除标签"}, (dialog, which) -> {
                        item.removeTag(tag);
                        adapter.notifyItemChanged(getAdapterPosition());
                        // 同时更新数据库
                        adapter.updateLinkTags(item);
                    })
                    .show();
        }

        // 格式化 URL 显示的辅助方法
        private String formatUrlForDisplay(String url) {
            final int MAX_URL_LENGTH = 200;
            if (url == null) return "";
            
            if (url.length() > MAX_URL_LENGTH) {
                // 保留开头的 scheme 和域名
                int schemeEnd = url.indexOf("://");
                if (schemeEnd != -1) {
                    schemeEnd += 3;  // 包含 "://"
                    int firstSlash = url.indexOf('/', schemeEnd);
                    if (firstSlash != -1) {
                        // 显示格式：scheme://domain/...省略部分.../最后一段
                        String start = url.substring(0, firstSlash);
                        String end = url.substring(url.length() - 30);  // 保留最后30个字符
                        return start + "/.../" + end;
                    }
                }
                
                // 如果 URL 格式不标准，简单截断
                return url.substring(0, MAX_URL_LENGTH - 3) + "...";
            }
            
            return url;
        }
    }

    static class DateHeaderViewHolder extends RecyclerView.ViewHolder {
        TextView dateText;

        DateHeaderViewHolder(View view) {
            super(view);
            dateText = view.findViewById(R.id.text_date);
        }

        void bind(String date) {
            dateText.setText(date);
        }
    }

    public void setPinnedLinks(List<LinkItem> pinnedLinks) {
        this.pinnedLinks = pinnedLinks;
        notifyDataSetChanged();
    }

    private void openLink(Context context, String url, int position) {
        try {
            Log.d("LinksAdapter", "openLink called, position: " + position + ", url: " + url);
            
            // 检查位置是否有效
            if (position < 0 || position >= items.size()) {
                Log.e("LinksAdapter", "Invalid position: " + position);
                return;
            }

            // 获取当前点击的LinkItem
            Object item = items.get(position);
            if (!(item instanceof LinkItem)) {
                Log.e("LinksAdapter", "Item at position " + position + " is not a LinkItem");
                return;
            }

            LinkItem linkItem = (LinkItem) item;
            Log.d("LinksAdapter", "LinkItem found: " + linkItem.getTitle());

            // 更新点击次数
            linkItem.incrementClickCount();
            Log.d("LinksAdapter", "Click count updated to: " + linkItem.getClickCount());
            linkDao.updateClickCount(linkItem.getId(), linkItem.getClickCount());

            // 刷新当前项
            notifyItemChanged(position);

            // 打开链接
            Log.d("LinksAdapter", "Starting WebViewActivity with url: " + url);
            Intent intent = new Intent(context, WebViewActivity.class);
            intent.putExtra("url", url);
            context.startActivity(intent);
            
        } catch (Exception e) {
            Log.e("LinksAdapter", "Error in openLink: " + e.getMessage(), e);
            Toast.makeText(context, "无法打开链接: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public int getPositionForDate(String date) {
        int position = 0;
        // 如果有置顶链接，需要加上置顶区域的位置
        if (!pinnedLinks.isEmpty()) {
            position += pinnedLinks.size() + 1;  // +1 for "置顶" header
        }

        for (Map.Entry<String, List<LinkItem>> entry : groupedLinks.entrySet()) {
            if (entry.getKey().equals(date)) {
                return position;
            }
            position += entry.getValue().size() + 1; // +1 for header
        }
        return -1;
    }

    /**
     * 为RecyclerView添加滑动操作功能
     * @param recyclerView 要添加滑动功能的RecyclerView
     */
    public void enableSwipeActions(RecyclerView recyclerView) {
        swipeActionsHelper.attachToRecyclerView(recyclerView);
    }
    
    /**
     * 检查适配器是否处于选择模式
     * @return 是否处于选择模式
     */
    public boolean isInSelectionMode() {
        return isSelectionMode;
    }
} 