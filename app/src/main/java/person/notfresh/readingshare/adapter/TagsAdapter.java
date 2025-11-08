package person.notfresh.readingshare.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.graphics.Typeface;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import person.notfresh.readingshare.R;

public class TagsAdapter extends RecyclerView.Adapter<TagsAdapter.TagViewHolder> {
    private List<TagItem> tags;
    private OnTagClickListener onTagClickListener;
    private OnTagLongClickListener onTagLongClickListener;
    private Set<String> highlightedTags = new HashSet<>();
    private Set<String> selectedTagNames = new HashSet<>();

    public interface OnTagClickListener {
        void onTagClick(int position, TagItem tag);
    }

    public interface OnTagLongClickListener {
        void onTagLongClick(int position, TagItem tag);
    }

    public TagsAdapter() {
        this.tags = new ArrayList<>();
    }

    public void setTags(List<TagItem> tags) {
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setHighlightedTags(Set<String> highlightedTags) {
        this.highlightedTags = highlightedTags != null ? new HashSet<>(highlightedTags) : new HashSet<>();
        notifyDataSetChanged();
    }
    
    public void setSelectedTagNames(Set<String> selectedTagNames) {
        this.selectedTagNames = selectedTagNames != null ? new HashSet<>(selectedTagNames) : new HashSet<>();
        notifyDataSetChanged();
    }

    public void swapItems(int fromPos, int toPos) {
        Collections.swap(tags, fromPos, toPos);
        notifyItemMoved(fromPos, toPos);
    }

    public TagItem getItem(int position) {
        if (position >= 0 && position < tags.size()) {
            return tags.get(position);
        }
        return null;
    }

    public List<TagItem> getTags() {
        return new ArrayList<>(tags);
    }

    public void setOnTagClickListener(OnTagClickListener listener) {
        this.onTagClickListener = listener;
    }

    public void setOnTagLongClickListener(OnTagLongClickListener listener) {
        this.onTagLongClickListener = listener;
    }

    @NonNull
    @Override
    public TagViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tag_with_count, parent, false);
        return new TagViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TagViewHolder holder, int position) {
        TagItem tag = tags.get(position);
        holder.bind(tag, highlightedTags.contains(tag.getName()), 
                   selectedTagNames.contains(tag.getName()));
    }

    @Override
    public int getItemCount() {
        return tags.size();
    }

    class TagViewHolder extends RecyclerView.ViewHolder {
        private TextView tagText;
        private TextView countText;
        private View itemView;

        TagViewHolder(@NonNull View itemView) {
            super(itemView);
            this.itemView = itemView;
            tagText = itemView.findViewById(R.id.text_tag);
            countText = itemView.findViewById(R.id.text_count);
        }

        void bind(TagItem tag, boolean isHighlighted, boolean isSelected) {
            tagText.setText(tag.getName());
            countText.setText(String.valueOf(tag.getCount()));

            // 设置样式
            if (isSelected) {
                itemView.setBackgroundResource(R.drawable.tag_background_selected);
                tagText.setTextColor(itemView.getContext().getResources()
                        .getColor(android.R.color.white, null));
                countText.setTextColor(itemView.getContext().getResources()
                        .getColor(android.R.color.white, null));
            } else if (isHighlighted) {
                itemView.setBackgroundResource(R.drawable.tag_background_highlighted);
                tagText.setTextColor(itemView.getContext().getResources()
                        .getColor(R.color.tag_highlight_color, null));
                tagText.setTypeface(tagText.getTypeface(), Typeface.BOLD);
                countText.setTextColor(itemView.getContext().getResources()
                        .getColor(android.R.color.black, null));
            } else {
                itemView.setBackgroundResource(R.drawable.tag_background_normal);
                tagText.setTextColor(itemView.getContext().getResources()
                        .getColor(android.R.color.black, null));
                tagText.setTypeface(tagText.getTypeface(), Typeface.NORMAL);
                countText.setTextColor(itemView.getContext().getResources()
                        .getColor(android.R.color.black, null));
            }

            itemView.setOnClickListener(v -> {
                if (onTagClickListener != null) {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        onTagClickListener.onTagClick(pos, tag);
                    }
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (onTagLongClickListener != null) {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        onTagLongClickListener.onTagLongClick(pos, tag);
                        return true;
                    }
                }
                return false;
            });
        }
    }

    public static class TagItem {
        private long id;
        private String name;
        private int count;

        public TagItem(long id, String name, int count) {
            this.id = id;
            this.name = name;
            this.count = count;
        }

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public int getCount() {
            return count;
        }
    }
}

