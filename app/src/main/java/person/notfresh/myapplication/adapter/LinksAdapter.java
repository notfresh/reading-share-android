package person.notfresh.myapplication.adapter;

import android.app.AlertDialog;
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

import person.notfresh.myapplication.R;
import person.notfresh.myapplication.model.LinkItem;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.flexbox.FlexboxLayout;
import person.notfresh.myapplication.db.LinkDao;
import person.notfresh.myapplication.util.ExportUtil;
import person.notfresh.myapplication.util.BilibiliUrlConverter;
import java.io.IOException;
import person.notfresh.myapplication.util.AppUtils;

public class LinksAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_DATE_HEADER = 0;
    private static final int TYPE_LINK_ITEM = 1;
    
    private List<Object> items = new ArrayList<>();
    private List<Object> originalItems = new ArrayList<>();  // 存储原始数据
    private OnLinkActionListener listener;
    private LinkDao linkDao;
    private static Context context;  // 添加 context 引用
    private Set<LinkItem> selectedItems = new HashSet<>();
    private boolean isSelectionMode = false;
    private List<LinkItem> links = new ArrayList<>();

    public interface OnLinkActionListener {
        void onDeleteLink(LinkItem link);
        void onUpdateLink(LinkItem oldLink, String newTitle);
        void addTagToLink(LinkItem item, String tag);
        void updateLinkTags(LinkItem item);
        void onEnterSelectionMode();  // 添加新的回调方法
    }

    public LinksAdapter(Context context) {
        this.context = context;
        this.linkDao = new LinkDao(context);
        this.linkDao.open();
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
            ((LinkViewHolder) holder).bind((LinkItem) items.get(position));
        }

    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void setGroupedLinks(Map<String, List<LinkItem>> groupedLinks) {
        items.clear();
        originalItems.clear();
        
        for (Map.Entry<String, List<LinkItem>> entry : groupedLinks.entrySet()) {
            items.add(entry.getKey());
            items.addAll(entry.getValue());
        }
        
        // 保存原始数据
        originalItems.addAll(items);
        notifyDataSetChanged();
    }

    public void addTagToLink(LinkItem item, String tagName) {
        item.addTag(tagName);
        notifyDataSetChanged();
        linkDao.updateLinkTags(item);
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
        popup.getMenu().add(0, 4, 0, "多选模式");
        
        popup.setOnMenuItemClickListener(menuItem -> {
            switch (menuItem.getItemId()) {
                case 1:
                    showEditTitleDialog(view, item);
                    return true;
                case 2:
                    showDeleteConfirmDialog(item);
                    return true;
                case 3:
                    shareAsText(item);
                    return true;
                case 4:
                    // 进入多选模式并选中当前项
                    if (listener != null) {
                        listener.onEnterSelectionMode();
                    }
                    toggleItemSelection(item);
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
        isSelectionMode = !isSelectionMode;
        if (!isSelectionMode) {
            selectedItems.clear();
        }
        notifyDataSetChanged();
        Log.d("LinksAdapter", "Selection mode toggled to: " + isSelectionMode);  // 添加日志
    }

    public void toggleItemSelection(LinkItem item) {
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

    private String getItemUrl(int position) {
        if (items.get(position) instanceof LinkItem) {
            return ((LinkItem) items.get(position)).getUrl();
        }
        return null;
    }

    static class LinkViewHolder extends RecyclerView.ViewHolder {
        TextView titleText;
        TextView urlText;
        FlexboxLayout tagContainer;
        Button addTagButton;
        private final LinksAdapter adapter;

        LinkViewHolder(View view, LinksAdapter adapter) {
            super(view);
            this.adapter = adapter;
            titleText = view.findViewById(R.id.text_title);
            urlText = view.findViewById(R.id.text_url);
            tagContainer = view.findViewById(R.id.tag_container);
            addTagButton = view.findViewById(R.id.btn_add_tag);
        }

        void bind(LinkItem item) {
            titleText.setText(item.getTitle());
            
            // 处理 URL 显示
            String displayUrl = formatUrlForDisplay(item.getUrl());
            urlText.setText(displayUrl);
            
            // 显示标签
            tagContainer.removeAllViews();
            for (String tag : item.getTags()) {
                addTagView(tag, item);
            }

            // 添加标签按钮
            addTagButton.setOnClickListener(v -> showAddTagDialog(v.getContext(), item));

            // 添加选择模式的视觉反馈
            if (adapter.isSelectionMode) {
                itemView.setBackgroundResource(
                    adapter.selectedItems.contains(item) ? 
                    R.drawable.selected_background : 
                    R.drawable.normal_background
                );
                
                // 在选择模式下，点击切换选中状态
                itemView.setOnClickListener(v -> {
                    adapter.toggleItemSelection(item);
                    // 更新视觉效果
                    itemView.setBackgroundResource(
                        adapter.selectedItems.contains(item) ? 
                        R.drawable.selected_background : 
                        R.drawable.normal_background
                    );
                });
            } else {
                itemView.setBackgroundResource(R.drawable.normal_background);
                // 正常模式下的点击和长按处理
                itemView.setOnClickListener(v -> {
                    try {
                        String url = adapter.extractRealUrl(item.getUrl());
                        String sourceApp = item.getSourceApp();
                        Context context = v.getContext();

                        // 创建基础 Intent
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                        if (url.contains("b23.tv")) {
                            handleBilibiliLink(url);
                        }else{
                            // 其他应用使用通用处理方式
                            intent.setData(Uri.parse(url));
                        
                        
                            // 如果有源应用，先尝试用源应用打开
                            if (sourceApp != null && !sourceApp.isEmpty()) {
                                try {
                                    intent.setPackage(sourceApp);
                                    if (adapter.isIntentAvailable(context, intent)) {
                                        context.startActivity(intent);
                                        return;
                                    }
                                } catch (Exception ignored) {
                                    // 如果源应用不可用，继续使用通用方式打开
                                }
                            }

                            // 如果源应用无法打开，移除包名限制
                            intent.setPackage(null);
                        
                        
                            // 使用系统选择器打开
                            Intent chooserIntent = Intent.createChooser(intent, "选择打开方式");
                            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(chooserIntent);
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
        }

        private void addTagView(String tag, LinkItem item) {
            TextView tagView = (TextView) LayoutInflater.from(itemView.getContext())
                    .inflate(R.layout.item_tag, tagContainer, false);
            tagView.setText(tag);
            tagView.setOnClickListener(v -> showTagOptionsDialog(v.getContext(), tag, item));
            tagContainer.addView(tagView);
        }

        private void showAddTagDialog(Context context, LinkItem item) {
            EditText input = new EditText(context);
            input.setHint("输入标签名称");

            new AlertDialog.Builder(context)
                    .setTitle("添加标签")
                    .setView(input)
                    .setPositiveButton("确定", (dialog, which) -> {
                        String tagName = input.getText().toString().trim();
                        if (!tagName.isEmpty()) {
                            adapter.addTagToLink(item, tagName);
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
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
} 