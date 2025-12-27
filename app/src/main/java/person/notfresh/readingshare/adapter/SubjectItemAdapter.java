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
import java.util.ArrayList;
import java.util.List;

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
    private OnSubjectItemActionListener actionListener;
    private Context context;
    private LinkDao linkDao;
    private SubjectDao subjectDao;

    public interface OnSubjectItemClickListener {
        void onSubjectItemClick(SubjectItem item);
    }

    public interface OnSubjectItemActionListener {
        void onCollectLink(SubjectItem item, LinkItem linkItem);
        void onDeleteSubjectItem(SubjectItem item);
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

    public void setOnSubjectItemActionListener(OnSubjectItemActionListener listener) {
        this.actionListener = listener;
    }

    public void setItems(List<SubjectItem> items) {
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * 获取当前的主题项列表（用于拖拽排序）
     */
    public List<SubjectItem> getItems() {
        return items;
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

        // 获取链接信息
        LinkItem linkItem = getLinkById(item.getLinkId());
        if (linkItem == null) {
            Toast.makeText(context, "无法获取链接信息", Toast.LENGTH_SHORT).show();
            return;
        }

        // 打开链接
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
        
        popup.getMenu().add(0, 2, 0, "编辑");
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
                    if (listener != null) {
                        listener.onSubjectItemClick(item);
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
        private LinearLayout layoutLink;
        private TextView textLinkTitle;
        private TextView textLinkUrl;
        private TextView textLinkDeleted;
        private TextView textRemark;
        private ImageAdapter imageAdapter;

        SubjectItemViewHolder(@NonNull View itemView) {
            super(itemView);
            recyclerImages = itemView.findViewById(R.id.recycler_images);
            layoutLink = itemView.findViewById(R.id.layout_link);
            textLinkTitle = itemView.findViewById(R.id.text_link_title);
            textLinkUrl = itemView.findViewById(R.id.text_link_url);
            textLinkDeleted = itemView.findViewById(R.id.text_link_deleted);
            textRemark = itemView.findViewById(R.id.text_remark);

            // 设置图片横向滚动
            recyclerImages.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
            imageAdapter = new ImageAdapter(context);
            recyclerImages.setAdapter(imageAdapter);

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
            } else {
                recyclerImages.setVisibility(View.GONE);
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
        }
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
    private static class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ImageViewHolder> {
        private List<String> images = new ArrayList<>();
        private Context context;

        ImageAdapter(Context context) {
            this.context = context;
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
            }
        }
    }
}

