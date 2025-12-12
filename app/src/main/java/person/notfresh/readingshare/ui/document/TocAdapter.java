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

public class TocAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_PAGE = 0;
    private static final int VIEW_TYPE_OUTLINE = 1;

    private List<Integer> pageNumbers;
    private List<TocOutlineItem> outlineItems;
    private int currentPage;
    private boolean isPageView = true; // true=页码视图, false=目录视图
    private OnPageClickListener listener;

    public interface OnPageClickListener {
        void onPageClick(int pageIndex);
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
        this.isPageView = isPageView;
        notifyDataSetChanged();
    }

    public void setOutlineItems(List<TocOutlineItem> outlineItems) {
        this.outlineItems = outlineItems != null ? outlineItems : new ArrayList<>();
        if (!isPageView) {
            notifyDataSetChanged();
        }
    }

    @Override
    public int getItemViewType(int position) {
        return isPageView ? VIEW_TYPE_PAGE : VIEW_TYPE_OUTLINE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_PAGE) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_toc_page, parent, false);
            return new PageViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_toc_outline, parent, false);
            return new OutlineViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof PageViewHolder) {
            bindPageViewHolder((PageViewHolder) holder, position);
        } else if (holder instanceof OutlineViewHolder) {
            bindOutlineViewHolder((OutlineViewHolder) holder, position);
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

    @Override
    public int getItemCount() {
        if (isPageView) {
            return pageNumbers != null ? pageNumbers.size() : 0;
        } else {
            // 如果没有目录项，至少显示一个"暂无目录"的提示
            return outlineItems.isEmpty() ? 1 : outlineItems.size();
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
}

