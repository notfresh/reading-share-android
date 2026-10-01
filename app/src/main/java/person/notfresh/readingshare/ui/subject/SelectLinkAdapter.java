package person.notfresh.readingshare.ui.subject;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.model.LinkItem;

/**
 * 主题详情页"选链接"列表的 RecyclerView.Adapter。
 *
 * <p>职责单一：渲染 {@link LinkItem}，把点击事件交回给 DialogFragment 决定。
 * <ul>
 *   <li>不直接读 DAO / DB。</li>
 *   <li>不感知"已收录 / 取消收录"业务，只通过 {@link #disabledIds} 让项置灰。</li>
 *   <li>不耦合搜索 / 分页逻辑，只接收一个 {@link List<LinkItem>}。</li>
 * </ul>
 */
class SelectLinkAdapter extends RecyclerView.Adapter<SelectLinkAdapter.LinkViewHolder> {

    /** 点击回调：把被点的 linkId 抛回 DialogFragment 决定下一步。 */
    interface OnLinkClickListener {
        void onLinkClick(LinkItem item);
    }

    private final List<LinkItem> items = new ArrayList<>();
    /** 已收录到当前主题的 linkId 集合 → 置灰且点击走"取消收录"分支。 */
    private Set<Long> disabledIds = java.util.Collections.emptySet();
    private OnLinkClickListener clickListener;

    void setItems(List<LinkItem> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    void appendItems(List<LinkItem> more) {
        if (more == null || more.isEmpty()) return;
        int start = items.size();
        items.addAll(more);
        notifyItemRangeInserted(start, more.size());
    }

    void setDisabledIds(Set<Long> disabledIds) {
        this.disabledIds = disabledIds == null
                ? java.util.Collections.<Long>emptySet()
                : disabledIds;
        notifyDataSetChanged();
    }

    void setOnLinkClickListener(OnLinkClickListener listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public LinkViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_link_picker, parent, false);
        return new LinkViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull LinkViewHolder holder, int position) {
        LinkItem item = items.get(position);
        holder.bind(item, disabledIds.contains(item.getId()), clickListener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class LinkViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout root;
        private final TextView titleView;
        private final TextView urlView;
        private final TextView tagsView;

        LinkViewHolder(@NonNull View itemView) {
            super(itemView);
            root = (LinearLayout) itemView;
            titleView = itemView.findViewById(R.id.picker_title);
            urlView = itemView.findViewById(R.id.picker_url);
            tagsView = itemView.findViewById(R.id.picker_tags);
        }

        void bind(final LinkItem item, boolean disabled, OnLinkClickListener listener) {
            String title = item.getTitle();
            titleView.setText(title != null && !title.isEmpty() ? title : "(无标题)");
            urlView.setText(item.getUrl());

            List<String> tags = item.getTags();
            if (tags == null || tags.isEmpty()) {
                tagsView.setText("无标签");
            } else {
                tagsView.setText(android.text.TextUtils.join(" · ", tags));
            }

            // 置灰：透明度 0.45，仍可点击（由 DialogFragment 决定是"取消收录"还是 noop）
            root.setAlpha(disabled ? 0.45f : 1.0f);

            root.setOnClickListener(v -> {
                if (listener != null) listener.onLinkClick(item);
            });
        }
    }
}