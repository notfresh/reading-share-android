package person.notfresh.readingshare.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.model.LinkHistoryItem;

public class LinksHistoryAdapter extends RecyclerView.Adapter<LinksHistoryAdapter.ViewHolder> {
    public interface Listener {
        void onHistoryClick(LinkHistoryItem item);
        void onDeleteClick(LinkHistoryItem item);
    }

    private final List<LinkHistoryItem> items;
    private final Listener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    public LinksHistoryAdapter(List<LinkHistoryItem> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_link_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LinkHistoryItem item = items.get(position);
        holder.title.setText(item.getTitle());
        holder.url.setText(item.getUrl());
        holder.visitedAt.setText(dateFormat.format(new Date(item.getVisitedAt())));
        holder.itemView.setOnClickListener(v -> listener.onHistoryClick(item));
        holder.deleteButton.setOnClickListener(v -> listener.onDeleteClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView url;
        final TextView visitedAt;
        final Button deleteButton;

        ViewHolder(View view) {
            super(view);
            title = view.findViewById(R.id.history_title);
            url = view.findViewById(R.id.history_url);
            visitedAt = view.findViewById(R.id.history_visited_at);
            deleteButton = view.findViewById(R.id.history_delete_button);
        }
    }
}
