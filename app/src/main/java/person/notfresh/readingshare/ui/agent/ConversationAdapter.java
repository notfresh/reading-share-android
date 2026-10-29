package person.notfresh.readingshare.ui.agent;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import person.notfresh.readingshare.R;

/**
 * 对话历史列表适配器 —— 只渲染 + 回调,不碰数据来源(AGENTS.md §3)。
 *
 * <p>过滤在**本地**做(按标题 contains、忽略大小写):会话最多 50 段,
 * 全量一次拉回来在内存里过滤,比每次按键都跨 JNI 回 Python 问一次快得多。</p>
 */
public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.Holder> {

    /** 一行会话。字段跟 Python 侧 list_conversations 的 JSON 对齐。 */
    public static final class Item {
        public final String id;
        public final String title;
        public final long updatedAt;
        public final int rounds;

        public Item(String id, String title, long updatedAt, int rounds) {
            this.id = id;
            this.title = title;
            this.updatedAt = updatedAt;
            this.rounds = rounds;
        }
    }

    public interface OnConversationClickListener {
        void onConversationClicked(Item item);
    }

    private final List<Item> all = new ArrayList<>();
    private final List<Item> shown = new ArrayList<>();
    private final OnConversationClickListener listener;
    private String filter = "";
    private String currentId = "";

    public ConversationAdapter(OnConversationClickListener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    /** 整批替换(数据来自 Python 侧,一次给全)。 */
    public void submit(List<Item> items, String currentConversationId) {
        all.clear();
        all.addAll(items);
        currentId = currentConversationId == null ? "" : currentConversationId;
        applyFilter();
    }

    /** 按标题过滤(忽略大小写)。 */
    public void filter(String keyword) {
        filter = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        applyFilter();
    }

    private void applyFilter() {
        shown.clear();
        for (Item item : all) {
            if (TextUtils.isEmpty(filter) || item.title.toLowerCase(Locale.ROOT).contains(filter)) {
                shown.add(item);
            }
        }
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return shown.isEmpty();
    }

    public int size() {
        return shown.size();
    }

    @Override
    public long getItemId(int position) {
        return shown.get(position).id.hashCode();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Item item = shown.get(position);
        boolean isCurrent = item.id.equals(currentId);
        holder.title.setText(item.title);
        holder.title.setAlpha(isCurrent ? 1f : 0.9f);
        holder.meta.setText(holder.itemView.getContext().getString(
                R.string.agent_history_meta, item.rounds, relativeTime(holder, item.updatedAt)));
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onConversationClicked(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return shown.size();
    }

    /** 相对时间(文案走 string 资源,跟项目 i18n 约定一致)。 */
    private static String relativeTime(Holder holder, long epochSeconds) {
        android.content.Context ctx = holder.itemView.getContext();
        long diff = System.currentTimeMillis() / 1000L - epochSeconds;
        if (diff < 60) {
            return ctx.getString(R.string.agent_time_just_now);
        }
        if (diff < 3600) {
            return ctx.getString(R.string.agent_time_minutes, diff / 60);
        }
        if (diff < 86400) {
            return ctx.getString(R.string.agent_time_hours, diff / 3600);
        }
        return ctx.getString(R.string.agent_time_days, diff / 86400);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView meta;

        Holder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.conversation_title);
            meta = itemView.findViewById(R.id.conversation_meta);
        }
    }
}
