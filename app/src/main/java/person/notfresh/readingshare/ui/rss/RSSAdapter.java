package person.notfresh.readingshare.ui.rss;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import person.notfresh.readingshare.R;
import person.notfresh.readingshare.model.RssEntry;

public class RSSAdapter extends RecyclerView.Adapter<RSSAdapter.RSSViewHolder> {

    private List<RssEntry> rssEntries;
    private Context context;

    public RSSAdapter(Context context, List<RssEntry> rssEntries) {
        this.context = context;
        this.rssEntries = rssEntries;
    }

    public void updateEntries(List<RssEntry> entries) {
        if (entries == null) {
            Log.e("RSSAdapter", "Received null entries list");
            return;
        }
        this.rssEntries.clear();
        this.rssEntries.addAll(entries);
        notifyDataSetChanged();
    }

    public void addEntries(List<RssEntry> newEntries) {
        if (newEntries == null) {
            Log.e("RSSAdapter", "Received null entries list");
            return;
        }
        int startPos = rssEntries.size();
        rssEntries.addAll(newEntries);
        notifyItemRangeInserted(startPos, newEntries.size());
    }

    public void clearEntries() {
        rssEntries.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RSSViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_link_rss, parent, false);
        return new RSSViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RSSViewHolder holder, int position) {
        RssEntry entry = rssEntries.get(position);
        holder.titleTextView.setText(entry.getTitle());
        holder.linkTextView.setText(entry.getLink());

        holder.itemView.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(entry.getLink()));
            context.startActivity(browserIntent);
        });

        holder.itemView.setOnLongClickListener(v -> {
            showActionDialog(entry, holder.itemView);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return rssEntries.size();
    }

    private void showActionDialog(RssEntry entry, View anchor) {
        PopupMenu popupMenu = new PopupMenu(context, anchor);
        popupMenu.getMenuInflater().inflate(R.menu.rss_item_menu, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_share) {
                shareEntry(entry);
                return true;
            } else if (itemId == R.id.action_copy_link) {
                copyLinkToClipboard(entry.getLink());
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void shareEntry(RssEntry entry) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, entry.getLink());
        context.startActivity(Intent.createChooser(shareIntent, "Share link via"));
    }

    private void copyLinkToClipboard(String link) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("RSS Link", link);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    static class RSSViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView;
        TextView linkTextView;

        RSSViewHolder(View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.rss_entry_title);
            linkTextView = itemView.findViewById(R.id.rss_entry_link);
        }
    }
} 