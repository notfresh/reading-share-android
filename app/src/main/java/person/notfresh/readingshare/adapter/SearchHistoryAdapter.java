package person.notfresh.readingshare.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.model.SearchHistoryItem;

/**
 * 搜索历史下拉行适配器
 * - pinned 在前、unpinned 在后（由 SearchHistoryLogic.sortItems 保证）
 * - 空状态显示"暂无搜索历史"占位行（disable）
 */
public class SearchHistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ITEM = 0;
    private static final int TYPE_EMPTY = 1;

    public interface OnItemClickListener {
        void onItemClick(SearchHistoryItem item);
        void onPinClick(SearchHistoryItem item);
        void onDeleteClick(SearchHistoryItem item);
    }

    private final List<SearchHistoryItem> items = new ArrayList<>();
    private OnItemClickListener listener;

    public void setItems(List<SearchHistoryItem> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener l) {
        this.listener = l;
    }

    @Override
    public int getItemViewType(int position) {
        return items.isEmpty() ? TYPE_EMPTY : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_EMPTY) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_history_empty, parent, false);
            return new EmptyVH(v);
        }
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_search_history, parent, false);
        return new ItemVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof EmptyVH) return;
        SearchHistoryItem item = items.get(position);
        ItemVH vh = (ItemVH) holder;
        vh.text.setText(item.getText());
        vh.pin.setImageResource(item.isPinned() ? R.drawable.ic_pin_filled : R.drawable.ic_pin_outline);
        vh.pin.setContentDescription(vh.itemView.getContext()
            .getString(item.isPinned() ? R.string.search_history_unpin : R.string.search_history_pin));

        vh.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
        vh.pin.setOnClickListener(v -> {
            if (listener != null) listener.onPinClick(item);
        });
        vh.delete.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteClick(item);
        });
    }

    @Override
    public int getItemCount() {
        // 空时仍显示 1 个占位行
        return items.isEmpty() ? 1 : items.size();
    }

    static class ItemVH extends RecyclerView.ViewHolder {
        final TextView text;
        final ImageButton pin;
        final ImageButton delete;
        ItemVH(View v) {
            super(v);
            text = v.findViewById(R.id.search_history_text);
            pin = v.findViewById(R.id.search_history_pin);
            delete = v.findViewById(R.id.search_history_delete);
        }
    }

    static class EmptyVH extends RecyclerView.ViewHolder {
        EmptyVH(View v) { super(v); }
    }
}
