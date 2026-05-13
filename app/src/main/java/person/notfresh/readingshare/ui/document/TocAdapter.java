package person.notfresh.readingshare.ui.document;

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
import person.notfresh.readingshare.model.BookmarkItem;

public class TocAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_PAGE = 0;
    private static final int VIEW_TYPE_OUTLINE = 1;
    private static final int VIEW_TYPE_BOOKMARK = 2;

    private List<Integer> pageNumbers;
    private List<TocOutlineItem> outlineItems;
    private List<BookmarkItem> bookmarkItems = new ArrayList<>();
    private int currentPage;
    public enum ViewType { PAGE, OUTLINE, BOOKMARK }
    private ViewType currentViewType = ViewType.PAGE;
    private OnPageClickListener listener;

    public interface OnPageClickListener {
        void onPageClick(int pageIndex);
    }

    public interface OnBookmarkDeleteCallback {
        void onBookmarkDelete(long bookmarkId);
    }

    private OnBookmarkDeleteCallback bookmarkCallback;

    public void setOnBookmarkDeleteCallback(OnBookmarkDeleteCallback callback) {
        this.bookmarkCallback = callback;
    }

    public TocAdapter(List<Integer> pageNumbers, int currentPage) {
        this.pageNumbers = pageNumbers != null ? pageNumbers : new ArrayList<>();
        this.outlineItems = new ArrayList<>();
        this.currentPage = currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
        notifyDataSetChanged();
    }

    public void setOnPageClickListener(OnPageClickListener listener) {
        this.listener = listener;
    }

    public void setPageView(boolean isPageView) {
        if (isPageView) {
            currentViewType = ViewType.PAGE;
        } else {
            currentViewType = ViewType.OUTLINE;
        }
        notifyDataSetChanged();
    }

    public void setViewType(ViewType viewType) {
        this.currentViewType = viewType;
        notifyDataSetChanged();
    }

    public void setBookmarks(List<BookmarkItem> bookmarks) {
        this.bookmarkItems = bookmarks != null ? new ArrayList<>(bookmarks) : new ArrayList<>();
        if (currentViewType == ViewType.BOOKMARK) {
            notifyDataSetChanged();
        }
    }

    public void setOutlineItems(List<TocOutlineItem> outlineItems) {
        this.outlineItems = outlineItems != null ? outlineItems : new ArrayList<>();
        if (currentViewType == ViewType.OUTLINE) {
            notifyDataSetChanged();
        }
    }

    @Override
    public int getItemViewType(int position) {
        switch (currentViewType) {
            case PAGE: return VIEW_TYPE_PAGE;
            case OUTLINE: return VIEW_TYPE_OUTLINE;
            case BOOKMARK: return VIEW_TYPE_BOOKMARK;
            default: return VIEW_TYPE_PAGE;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_PAGE) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_toc_page, parent, false);
            return new PageViewHolder(view);
        } else if (viewType == VIEW_TYPE_OUTLINE) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_toc_outline, parent, false);
            return new OutlineViewHolder(view);
        } else if (viewType == VIEW_TYPE_BOOKMARK) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_toc_bookmark, parent, false);
            return new BookmarkViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_toc_page, parent, false);
            return new PageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof PageViewHolder) {
            bindPageViewHolder((PageViewHolder) holder, position);
        } else if (holder instanceof OutlineViewHolder) {
            bindOutlineViewHolder((OutlineViewHolder) holder, position);
        } else if (holder instanceof BookmarkViewHolder) {
            bindBookmarkViewHolder((BookmarkViewHolder) holder, position);
        }
    }

    private void bindPageViewHolder(PageViewHolder holder, int position) {
        int pageNumber = pageNumbers.get(position);
        int pageIndex = pageNumber - 1; // 转换为0-based索引

        holder.tvPageNumber.setText(String.format(Locale.getDefault(), "%d", pageNumber));
        holder.tvPageTitle.setText(String.format(Locale.getDefault(), "第 %d 页", pageNumber));

        // 确保itemView可点击
        holder.itemView.setClickable(true);
        holder.itemView.setFocusable(true);

        // 高亮当前页
        if (pageIndex == currentPage) {
            holder.itemView.setBackgroundColor(
                    holder.itemView.getContext().getResources()
                            .getColor(android.R.color.darker_gray, null));
        } else {
            // 使用null让布局中的selectableItemBackground生效
            holder.itemView.setBackground(null);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPageClick(pageIndex);
            }
        });
    }

    private void bindOutlineViewHolder(OutlineViewHolder holder, int position) {
        if (outlineItems.isEmpty()) {
            // 如果没有目录，显示提示
            holder.tvOutlineTitle.setText("暂无目录");
            holder.tvOutlinePage.setText("");
            holder.itemView.setOnClickListener(null);
            holder.itemView.setClickable(false);
            holder.itemView.setFocusable(false);
            return;
        }

        TocOutlineItem item = outlineItems.get(position);
        holder.tvOutlineTitle.setText(item.getTitle());
        holder.tvOutlinePage.setText(String.format(Locale.getDefault(), "%d", item.getPageNumber()));

        // 根据层级设置缩进（使用paddingStart，因为布局已经设置了padding）
        int paddingStart = 16 + item.getLevel() * 24; // 每级缩进24dp，基础padding 16dp
        float density = holder.itemView.getContext().getResources().getDisplayMetrics().density;
        holder.itemView.setPadding(
            (int) (paddingStart * density),
            holder.itemView.getPaddingTop(),
            holder.itemView.getPaddingEnd(),
            holder.itemView.getPaddingBottom()
        );

        // 确保itemView可点击
        holder.itemView.setClickable(true);
        holder.itemView.setFocusable(true);

        // 高亮当前页
        if (item.getPageIndex() == currentPage) {
            holder.itemView.setBackgroundColor(
                    holder.itemView.getContext().getResources()
                            .getColor(android.R.color.darker_gray, null));
        } else {
            // 使用null让布局中的selectableItemBackground生效
            holder.itemView.setBackground(null);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPageClick(item.getPageIndex());
            }
        });
    }

    private void bindBookmarkViewHolder(BookmarkViewHolder holder, int position) {
        if (bookmarkItems.isEmpty()) {
            holder.tvBookmarkPage.setText("暂无书签");
            holder.tvBookmarkNote.setText("");
            holder.itemView.setOnClickListener(null);
            holder.itemView.setClickable(false);
            return;
        }

        BookmarkItem item = bookmarkItems.get(position);
        holder.tvBookmarkPage.setText(String.format(Locale.getDefault(), "第 %d 页", item.getPageNumber()));
        holder.tvBookmarkNote.setText(item.getNote() != null ? item.getNote() : "无备注");
        if (item.getNote() == null) {
            holder.tvBookmarkNote.setTextColor(holder.itemView.getContext().getResources()
                    .getColor(android.R.color.darker_gray, null));
        } else {
            holder.tvBookmarkNote.setTextColor(holder.itemView.getContext().getResources()
                    .getColor(android.R.color.black, null));
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPageClick(item.getPageIndex());
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            new android.app.AlertDialog.Builder(holder.itemView.getContext())
                    .setTitle("删除书签")
                    .setMessage("确定要删除书签 \"第 " + item.getPageNumber() + " 页\" 吗？")
                    .setPositiveButton("删除", (dialog, which) -> {
                        if (bookmarkCallback != null) {
                            bookmarkCallback.onBookmarkDelete(item.getId());
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        if (currentViewType == ViewType.BOOKMARK) {
            return bookmarkItems.isEmpty() ? 1 : bookmarkItems.size();
        } else if (currentViewType == ViewType.OUTLINE) {
            return outlineItems.isEmpty() ? 1 : outlineItems.size();
        } else {
            return pageNumbers != null ? pageNumbers.size() : 0;
        }
    }

    static class PageViewHolder extends RecyclerView.ViewHolder {
        TextView tvPageNumber;
        TextView tvPageTitle;

        PageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPageNumber = itemView.findViewById(R.id.tv_page_number);
            tvPageTitle = itemView.findViewById(R.id.tv_page_title);
        }
    }

    static class OutlineViewHolder extends RecyclerView.ViewHolder {
        TextView tvOutlineTitle;
        TextView tvOutlinePage;

        OutlineViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOutlineTitle = itemView.findViewById(R.id.tv_outline_title);
            tvOutlinePage = itemView.findViewById(R.id.tv_outline_page);
        }
    }

    static class BookmarkViewHolder extends RecyclerView.ViewHolder {
        TextView tvBookmarkPage;
        TextView tvBookmarkNote;

        BookmarkViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBookmarkPage = itemView.findViewById(R.id.tv_bookmark_page);
            tvBookmarkNote = itemView.findViewById(R.id.tv_bookmark_note);
        }
    }
}

