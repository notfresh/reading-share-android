package person.notfresh.readingshare.adapter;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.WebViewActivity;
import person.notfresh.readingshare.core.model.SubjectItem;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.db.SubjectDao;
import person.notfresh.readingshare.model.LinkItem;

/**
 * 主题项列表适配器
 */
public class SubjectItemAdapter extends RecyclerView.Adapter<SubjectItemAdapter.SubjectItemViewHolder> {
    List<SubjectItem> items = new ArrayList<>(); // package-private for drag-and-drop access
    private OnSubjectItemClickListener listener;
    private OnSubjectItemEditListener editListener;
    private OnSubjectItemActionListener actionListener;
    private Context context;
    private LinkDao linkDao;
    private SubjectDao subjectDao;
    private boolean archiveMode = false;

    public interface OnSubjectItemClickListener {
        void onSubjectItemClick(SubjectItem item);
    }

    public interface OnSubjectItemEditListener {
        void onSubjectItemEdit(SubjectItem item);
    }

    public interface OnSubjectItemActionListener {
        void onCollectLink(SubjectItem item, LinkItem linkItem);
        void onDeleteSubjectItem(SubjectItem item);
        void onArchiveSubjectItem(SubjectItem item);
        void onRestoreSubjectItem(SubjectItem item);
        void onRefreshItems();
    }

    public SubjectItemAdapter(Context context) {
        this.context = context;
        this.linkDao = new LinkDao(context);
        this.linkDao.open();
        this.subjectDao = new SubjectDao(context);
        this.subjectDao.open();
    }

    public void setOnSubjectItemClickListener(OnSubjectItemClickListener listener) {
        this.listener = listener;
    }

    public void setOnSubjectItemEditListener(OnSubjectItemEditListener editListener) {
        this.editListener = editListener;
    }

    public void setOnSubjectItemActionListener(OnSubjectItemActionListener listener) {
        this.actionListener = listener;
    }

    public void setItems(List<SubjectItem> items) {
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setArchiveMode(boolean archiveMode) {
        this.archiveMode = archiveMode;
    }

    public boolean isArchiveMode() {
        return archiveMode;
    }

    /**
     * 获取当前的主题项列表（用于拖拽排序）
     */
    public List<SubjectItem> getItems() {
        return items;
    }

    /**
     * 从适配器中直接移除主题项
     */
    public boolean removeItem(SubjectItem item) {
        if (item == null) {
            return false;
        }
        int position = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId() == item.getId()) {
                position = i;
                break;
            }
        }
        if (position == -1) {
            return false;
        }
        items.remove(position);
        notifyItemRemoved(position);
        return true;
    }

    public void close() {
        if (linkDao != null) {
            linkDao.close();
        }
        if (subjectDao != null) {
            subjectDao.close();
        }
    }

    /**
     * 处理链接点击（包括删除检测）
     */
    private void handleLinkClick(SubjectItem item) {
        // 检查链接是否存在
        boolean linkExists = subjectDao.isLinkItemExists(item.getLinkId());
        
        if (!linkExists || item.isLinkDeleted()) {
            // 链接已删除，提示用户
            new AlertDialog.Builder(context)
                    .setTitle("链接已删除")
                    .setMessage("该链接已被删除，是否删除此主题项？")
                    .setPositiveButton("删除", (dialog, which) -> {
                        // 删除主题项
                        if (actionListener != null) {
                            actionListener.onDeleteSubjectItem(item);
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }

        // 优先通过 listener 交给宿主 Activity 处理（例如 SubjectDetailActivity 会传递 context_ids）
        if (listener != null) {
            listener.onSubjectItemClick(item);
            return;
        }

        // 没有 listener 的回退：直接打开 WebView（保持原有行为）
        LinkItem linkItem = getLinkById(item.getLinkId());
        if (linkItem == null) {
            Toast.makeText(context, "无法获取链接信息", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = linkItem.getUrl();
        if (url != null && !url.isEmpty()) {
            Intent intent = new Intent(context, WebViewActivity.class);
            intent.putExtra("url", url);
            context.startActivity(intent);
        }
    }

    /**
     * 显示操作菜单（长按）
     */
    private void showActionMenu(View view, SubjectItem item) {
        PopupMenu popup = new PopupMenu(context, view);
        
        // 如果有链接，添加"收录链接"选项
        if (item.getLinkId() != null && item.getLinkId() > 0) {
            LinkItem linkItem = getLinkById(item.getLinkId());
            if (linkItem != null) {
                popup.getMenu().add(0, 1, 0, "收录链接");
            }
        }

        if (archiveMode) {
            popup.getMenu().add(0, 4, 0, "还原");
        } else {
            popup.getMenu().add(0, 4, 0, "归档");
        }
        
        if (listener != null && !archiveMode) {
            popup.getMenu().add(0, 2, 0, "编辑");
        }
        popup.getMenu().add(0, 3, 0, "删除");
        
        popup.setOnMenuItemClickListener(menuItem -> {
            switch (menuItem.getItemId()) {
                case 1: // 收录链接
                    if (item.getLinkId() != null && item.getLinkId() > 0) {
                        LinkItem linkItem = getLinkById(item.getLinkId());
                        if (linkItem != null && actionListener != null) {
                            actionListener.onCollectLink(item, linkItem);
                        }
                    }
                    return true;
                case 2: // 编辑
                    if (editListener != null) {
                        editListener.onSubjectItemEdit(item);
                    }
                    return true;
                case 3: // 删除
                    new AlertDialog.Builder(context)
                            .setTitle("确认删除")
                            .setMessage("确定要删除此主题项吗？")
                            .setPositiveButton("删除", (dialog, which) -> {
                                if (actionListener != null) {
                                    actionListener.onDeleteSubjectItem(item);
                                }
                            })
                            .setNegativeButton("取消", null)
                            .show();
                    return true;
                case 4: // 归档/还原
                    if (actionListener != null) {
                        if (archiveMode) {
                            actionListener.onRestoreSubjectItem(item);
                        } else {
                            actionListener.onArchiveSubjectItem(item);
                        }
                    }
                    return true;
                default:
                    return false;
            }
        });
        
        popup.show();
    }

    @NonNull
    @Override
    public SubjectItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_subject_item, parent, false);
        return new SubjectItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SubjectItemViewHolder holder, int position) {
        SubjectItem item = items.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class SubjectItemViewHolder extends RecyclerView.ViewHolder {
        private RecyclerView recyclerImages;
        private TextView textEmptyArea;
        private LinearLayout layoutLink;
        private TextView textLinkTitle;
        private TextView textLinkUrl;
        private TextView textLinkDeleted;
        private TextView textRemark;
        private TextView textAddTime;
        private ImageAdapter imageAdapter;

        SubjectItemViewHolder(@NonNull View itemView) {
            super(itemView);
            recyclerImages = itemView.findViewById(R.id.recycler_images);
            textEmptyArea = itemView.findViewById(R.id.text_empty_area);
            layoutLink = itemView.findViewById(R.id.layout_link);
            textLinkTitle = itemView.findViewById(R.id.text_link_title);
            textLinkUrl = itemView.findViewById(R.id.text_link_url);
            textLinkDeleted = itemView.findViewById(R.id.text_link_deleted);
            textRemark = itemView.findViewById(R.id.text_remark);
            textAddTime = itemView.findViewById(R.id.text_add_time);

            // 设置图片横向滚动
            recyclerImages.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
            imageAdapter = new ImageAdapter(context);
            recyclerImages.setAdapter(imageAdapter);

            // 在空白区域设置长按监听（用于只有图片时的长按触发）
            textEmptyArea.setOnLongClickListener(v -> {
                int position = getAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return false;
                
                SubjectItem item = items.get(position);
                showActionMenu(v, item);
                return true;
            });

            // 点击处理：如果是链接区域，打开链接；否则打开编辑对话框
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;
                
                SubjectItem item = items.get(position);
                
                // 如果点击的是链接区域，打开链接
                if (item.getLinkId() != null && item.getLinkId() > 0) {
                    handleLinkClick(item);
                } else {
                    // 否则打开编辑对话框
                    if (listener != null) {
                        listener.onSubjectItemClick(item);
                    }
                }
            });

            // 长按显示操作菜单
            itemView.setOnLongClickListener(v -> {
                int position = getAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return false;
                
                SubjectItem item = items.get(position);
                showActionMenu(v, item);
                return true;
            });
        }

        void bind(SubjectItem item) {
            // 显示图片
            if (item.getImages() != null && !item.getImages().isEmpty()) {
                recyclerImages.setVisibility(View.VISIBLE);
                imageAdapter.setImages(item.getImages());
                imageAdapter.setParentItem(item); // 设置父项，用于图片预览
                
                // 如果只有图片（没有链接和备注），显示空白区域用于长按触发
                boolean hasLink = item.getLinkId() != null && item.getLinkId() > 0;
                boolean hasRemark = item.getRemark() != null && !item.getRemark().trim().isEmpty();
                if (!hasLink && !hasRemark) {
                    textEmptyArea.setVisibility(View.VISIBLE);
                } else {
                    textEmptyArea.setVisibility(View.GONE);
                }
            } else {
                recyclerImages.setVisibility(View.GONE);
                textEmptyArea.setVisibility(View.GONE);
            }

            // 显示链接
            if (item.getLinkId() != null && item.getLinkId() > 0) {
                layoutLink.setVisibility(View.VISIBLE);
                if (item.isLinkDeleted()) {
                    // 链接已删除
                    textLinkTitle.setVisibility(View.GONE);
                    textLinkUrl.setVisibility(View.GONE);
                    textLinkDeleted.setVisibility(View.VISIBLE);
                } else {
                    // 加载链接信息（通过查询数据库）
                    LinkItem linkItem = getLinkById(item.getLinkId());
                    if (linkItem != null) {
                        textLinkTitle.setText(linkItem.getTitle() != null ? linkItem.getTitle() : "");
                        textLinkUrl.setText(linkItem.getUrl() != null ? linkItem.getUrl() : "");
                        textLinkTitle.setVisibility(View.VISIBLE);
                        textLinkUrl.setVisibility(View.VISIBLE);
                        textLinkDeleted.setVisibility(View.GONE);
                    } else {
                        // 链接不存在
                        textLinkTitle.setVisibility(View.GONE);
                        textLinkUrl.setVisibility(View.GONE);
                        textLinkDeleted.setVisibility(View.VISIBLE);
                    }
                }
            } else {
                layoutLink.setVisibility(View.GONE);
            }

            // 显示备注
            if (item.getRemark() != null && !item.getRemark().trim().isEmpty()) {
                textRemark.setVisibility(View.VISIBLE);
                textRemark.setText(item.getRemark());
            } else {
                textRemark.setVisibility(View.GONE);
            }

            // 显示时间（主列表=添加时间，归档列表=归档时间）
            long timeToShow = archiveMode ? item.getArchivedAt() : item.getAddTime();
            if (timeToShow > 0) {
                textAddTime.setVisibility(View.VISIBLE);
                textAddTime.setText(formatTimeLabel(timeToShow));
            } else {
                textAddTime.setVisibility(View.GONE);
            }
        }
    }

    private String formatTimeLabel(long timestamp) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        String prefix = archiveMode ? "归档于：" : "添加于：";
        return prefix + dateFormat.format(new Date(timestamp));
    }

    /**
     * 根据ID获取LinkItem（临时方法，应该移到LinkDao）
     */
    private LinkItem getLinkById(long linkId) {
        // TODO: 这个方法应该移到LinkDao中
        // 临时实现：通过查询所有链接来查找
        List<LinkItem> allLinks = linkDao.getAllLinks();
        for (LinkItem link : allLinks) {
            if (link.getId() == linkId) {
                return link;
            }
        }
        return null;
    }

    /**
     * 图片适配器（用于横向滚动显示图片）
     */
    private class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ImageViewHolder> {
        private List<String> images = new ArrayList<>();
        private Context context;
        private SubjectItem parentItem; // 父主题项，用于传递图片列表

        ImageAdapter(Context context) {
            this.context = context;
        }

        void setParentItem(SubjectItem item) {
            this.parentItem = item;
        }

        void setImages(List<String> images) {
            this.images = images != null ? new ArrayList<>(images) : new ArrayList<>();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView imageView = new ImageView(context);
            imageView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setAdjustViewBounds(true);
            imageView.setMaxWidth(200);
            imageView.setMaxHeight(200);
            return new ImageViewHolder(imageView);
        }

        @Override
        public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
            String imagePath = images.get(position);
            holder.bind(imagePath);
        }

        @Override
        public int getItemCount() {
            return images.size();
        }

        class ImageViewHolder extends RecyclerView.ViewHolder {
            private ImageView imageView;

            ImageViewHolder(@NonNull View itemView) {
                super(itemView);
                imageView = (ImageView) itemView;
            }

            void bind(String imagePath) {
                if (imagePath != null) {
                    File imageFile = new File(imagePath);
                    if (imageFile.exists()) {
                        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
                        if (bitmap != null) {
                            imageView.setImageBitmap(bitmap);
                        }
                    }
                }

                // 添加点击事件，打开图片预览
                imageView.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && parentItem != null && parentItem.getImages() != null) {
                        showImagePreview(parentItem.getImages(), position);
                    }
                });
            }

            /**
             * 显示图片预览对话框
             */
            private void showImagePreview(List<String> imagePaths, int currentIndex) {
                if (imagePaths == null || imagePaths.isEmpty()) {
                    return;
                }
                
                // 使用 DialogFragment 显示图片预览
                // ImagePreviewDialog 继承自 androidx.fragment.app.DialogFragment
                // 只能使用 androidx.fragment.app.FragmentManager（通过 getSupportFragmentManager()）
                if (context instanceof androidx.fragment.app.FragmentActivity) {
                    androidx.fragment.app.FragmentActivity activity = (androidx.fragment.app.FragmentActivity) context;
                    person.notfresh.readingshare.ui.subject.ImagePreviewDialog dialog = 
                        person.notfresh.readingshare.ui.subject.ImagePreviewDialog.newInstance(imagePaths, currentIndex);
                    dialog.show(activity.getSupportFragmentManager(), "ImagePreviewDialog");
                } else {
                    // 如果不是 FragmentActivity，无法显示 DialogFragment
                    android.widget.Toast.makeText(context, "无法显示图片预览", android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}

