package person.notfresh.myapplication.adapter;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import person.notfresh.myapplication.R;
import person.notfresh.myapplication.model.LinkItem;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.flexbox.FlexboxLayout;
import person.notfresh.myapplication.db.LinkDao;

public class LinksAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_DATE_HEADER = 0;
    private static final int TYPE_LINK_ITEM = 1;
    
    private List<Object> items = new ArrayList<>();
    private OnLinkActionListener listener;
    private LinkDao linkDao;
    private Context context;  // 添加 context 引用
    private Set<LinkItem> selectedItems = new HashSet<>();
    private boolean isSelectionMode = false;

    public interface OnLinkActionListener {
        void onDeleteLink(LinkItem link);
        void onUpdateLink(LinkItem oldLink, String newTitle);
        void addTagToLink(LinkItem item, String tag);
        void updateLinkTags(LinkItem item);
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
        for (Map.Entry<String, List<LinkItem>> entry : groupedLinks.entrySet()) {
            items.add(entry.getKey());
            items.addAll(entry.getValue());
        }
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

    void showActionDialog(View view, LinkItem item, int position) {
        String[] options = {"编辑标题", "删除"};
        
        new AlertDialog.Builder(view.getContext())
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showEditTitleDialog(view, item);
                            break;
                        case 1:
                            if (listener != null) {
                                listener.onDeleteLink(item);
                                items.remove(position);
                                notifyItemRemoved(position);
                            }
                            break;
                    }
                })
                .show();
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
            urlText.setText(item.getUrl());
            
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
                itemView.setOnClickListener(v -> {
                    try {
                        String url = adapter.extractRealUrl(item.getUrl());
                        String sourceApp = item.getSourceApp();
                        Context context = v.getContext();

                        // 创建基础 Intent
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                        if (url.contains("bilibili.com") || url.contains("b23.tv")) {
                            // B站链接处理
                            try {
                                Log.d("LinkOpen", "处理B站链接: " + url);
                                
                                // 创建专门的B站Intent
                                Intent biliIntent = new Intent(Intent.ACTION_VIEW);
                                biliIntent.setPackage("tv.danmaku.bili");
                                biliIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                
                                // 直接使用原始URL
                                biliIntent.setData(Uri.parse(url));
                                
                                // 尝试直接启动B站应用
                                if (adapter.isIntentAvailable(context, biliIntent)) {
                                    Log.d("LinkOpen", "启动B站应用");
                                    context.startActivity(biliIntent);
                                    return;
                                }
                                
                                Log.d("LinkOpen", "B站应用不可用，尝试其他方式");
                            } catch (Exception e) {
                                Log.e("LinkOpen", "B站处理失败: " + e.getMessage());
                            }
                        }

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

                    } catch (Exception e) {
                        Snackbar.make(v, "无法打开此链接: " + e.getMessage(), 
                                Snackbar.LENGTH_LONG).show();
                    }
                });
            }

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