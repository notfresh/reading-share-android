package person.notfresh.readingshare.adapter;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.model.DocumentItem;
import person.notfresh.readingshare.ui.document.DocumentViewerActivity;
import person.notfresh.readingshare.util.ShortcutUtil;

public class DocumentAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_DATE_HEADER = 0;
    private static final int TYPE_DOCUMENT_ITEM = 1;

    private List<Object> items = new ArrayList<>();
    private List<Object> originalItems = new ArrayList<>();
    private List<DocumentItem> pinnedDocuments = new ArrayList<>();
    private Map<String, List<DocumentItem>> groupedDocuments = new TreeMap<>(Collections.reverseOrder());
    private Context context;

    public interface OnDocumentActionListener {
        void onDeleteDocument(DocumentItem document);
        void onUpdateDocument(DocumentItem oldDocument, String newTitle);
        void onPinStatusChanged();
    }

    private OnDocumentActionListener listener;

    public DocumentAdapter(Context context) {
        this.context = context;
    }

    public void setOnDocumentActionListener(OnDocumentActionListener listener) {
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof String ? TYPE_DATE_HEADER : TYPE_DOCUMENT_ITEM;
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
                    .inflate(R.layout.item_document, parent, false);
            return new DocumentViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof DateHeaderViewHolder) {
            ((DateHeaderViewHolder) holder).bind((String) items.get(position));
        } else if (holder instanceof DocumentViewHolder) {
            DocumentItem item = (DocumentItem) items.get(position);
            ((DocumentViewHolder) holder).bind(item);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void setPinnedDocuments(List<DocumentItem> pinnedDocuments) {
        this.pinnedDocuments = pinnedDocuments;
        notifyDataSetChanged();
    }

    public void setGroupedDocuments(Map<String, List<DocumentItem>> groupedDocuments) {
        items.clear();
        originalItems.clear();
        this.groupedDocuments = groupedDocuments;

        // 首先添加置顶文档区域
        if (!pinnedDocuments.isEmpty()) {
            items.add("置顶");
            items.addAll(pinnedDocuments);
        }

        // 然后添加按日期分组的普通文档
        for (Map.Entry<String, List<DocumentItem>> entry : groupedDocuments.entrySet()) {
            items.add(entry.getKey());
            // 过滤掉已经在置顶区域显示的文档
            List<DocumentItem> normalDocs = new ArrayList<>();
            for (DocumentItem doc : entry.getValue()) {
                if (!pinnedDocuments.contains(doc)) {
                    normalDocs.add(doc);
                }
            }
            items.addAll(normalDocs);
        }

        // 保存原始数据
        originalItems.addAll(items);
        notifyDataSetChanged();
    }

    public void filter(String query) {
        query = query.toLowerCase().trim();
        items.clear();

        if (query.isEmpty()) {
            items.addAll(originalItems);
            notifyDataSetChanged();
            return;
        }

        Map<String, List<DocumentItem>> filteredGroups = new TreeMap<>(Collections.reverseOrder());
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        for (Object item : originalItems) {
            if (item instanceof String) {
                continue;
            }

            DocumentItem docItem = (DocumentItem) item;
            boolean matchesTitle = docItem.getTitle().toLowerCase().contains(query);
            boolean matchesType = docItem.getType().getDisplayName().toLowerCase().contains(query);

            if (matchesTitle || matchesType) {
                String date = dateFormat.format(new Date(docItem.getTimestamp()));
                filteredGroups.computeIfAbsent(date, k -> new ArrayList<>()).add(docItem);
            }
        }

        for (Map.Entry<String, List<DocumentItem>> entry : filteredGroups.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                items.add(entry.getKey());
                items.addAll(entry.getValue());
            }
        }

        notifyDataSetChanged();
    }

    public boolean removeDocumentItem(DocumentItem item) {
        if (item == null) {
            return false;
        }

        int position = -1;
        for (int i = 0; i < items.size(); i++) {
            Object obj = items.get(i);
            if (obj instanceof DocumentItem) {
                DocumentItem docItem = (DocumentItem) obj;
                if (docItem.getId() == item.getId()) {
                    position = i;
                    break;
                }
            }
        }

        if (position == -1) {
            return false;
        }

        items.remove(position);
        originalItems.remove(item);
        pinnedDocuments.removeIf(doc -> doc.getId() == item.getId());

        for (Map.Entry<String, List<DocumentItem>> entry : groupedDocuments.entrySet()) {
            entry.getValue().removeIf(doc -> doc.getId() == item.getId());
        }

        notifyItemRemoved(position);
        return true;
    }

    public int getPositionForDate(String date) {
        int position = 0;
        if (!pinnedDocuments.isEmpty()) {
            position += pinnedDocuments.size() + 1;
        }

        for (Map.Entry<String, List<DocumentItem>> entry : groupedDocuments.entrySet()) {
            if (entry.getKey().equals(date)) {
                return position;
            }
            position += entry.getValue().size() + 1;
        }
        return -1;
    }

    // ViewHolder 类
    class DocumentViewHolder extends RecyclerView.ViewHolder {
        TextView titleText;
        TextView typeText;
        TextView fileSizeText;
        TextView dateText;
        TextView clickCountText;
        TextView remarkText;

        DocumentViewHolder(View view) {
            super(view);
            titleText = view.findViewById(R.id.text_title);
            typeText = view.findViewById(R.id.text_document_type);
            fileSizeText = view.findViewById(R.id.text_file_size);
            dateText = view.findViewById(R.id.text_date);
            clickCountText = view.findViewById(R.id.click_count_text);
            remarkText = view.findViewById(R.id.text_remark);
        }

        void bind(DocumentItem item) {
            titleText.setText(item.getTitle());
            typeText.setText(item.getType().getDisplayName());
            fileSizeText.setText(item.getFormattedFileSize());
            
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            dateText.setText(dateFormat.format(new Date(item.getTimestamp())));
            
            clickCountText.setText("打开 " + item.getClickCount() + " 次");

            if (item.getRemark() != null && !item.getRemark().isEmpty()) {
                remarkText.setText("备注: " + item.getRemark());
                remarkText.setVisibility(View.VISIBLE);
            } else {
                remarkText.setVisibility(View.GONE);
            }

            // 点击打开文档
            itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, DocumentViewerActivity.class);
                intent.putExtra("document_id", item.getId());
                context.startActivity(intent);
            });

            // 长按显示操作菜单
            itemView.setOnLongClickListener(v -> {
                showPopupMenu(v, item);
                return true;
            });
        }
    }

    /**
     * 显示长按菜单
     */
    private void showPopupMenu(View view, DocumentItem item) {
        PopupMenu popupMenu = new PopupMenu(context, view);
        android.view.MenuInflater inflater = popupMenu.getMenuInflater();
        inflater.inflate(R.menu.document_item_menu, popupMenu.getMenu());
        
        popupMenu.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == R.id.action_share) {
                shareDocument(item);
                return true;
            } else if (id == R.id.action_rename) {
                showRenameDialog(view, item);
                return true;
            } else if (id == R.id.action_delete) {
                if (listener != null) {
                    listener.onDeleteDocument(item);
                }
                return true;
            }
            return false;
        });
        
        popupMenu.show();
    }

    /**
     * 显示重命名对话框
     * 复用 OnDocumentActionListener.onUpdateDocument 已有回调(由 DocumentFragment.onUpdateDocument 实现 DAO 写入 + loadDocuments)
     */
    private void showRenameDialog(View view, DocumentItem item) {
        EditText input = new EditText(view.getContext());
        input.setText(item.getTitle());
        input.setSelection(item.getTitle() != null ? item.getTitle().length() : 0);

        new AlertDialog.Builder(view.getContext())
                .setTitle("重命名")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String newTitle = input.getText().toString();
                    if (!newTitle.isEmpty() && listener != null) {
                        listener.onUpdateDocument(item, newTitle);
                    } else {
                        Toast.makeText(view.getContext(), "标题不能为空", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 分享文档文件
     */
    private void shareDocument(DocumentItem item) {
        File file = new File(item.getFilePath());
        if (!file.exists()) {
            Toast.makeText(context, "文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri fileUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".provider",
                    file
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(item.getType().getMimeType());
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, item.getTitle());
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(shareIntent, "分享文档");
            context.startActivity(chooser);
        } catch (Exception e) {
            Toast.makeText(context, "分享失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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

