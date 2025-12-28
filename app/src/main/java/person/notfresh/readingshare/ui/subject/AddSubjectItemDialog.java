package person.notfresh.readingshare.ui.subject;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.core.model.SubjectItem;
import person.notfresh.readingshare.core.model.SubjectUtil;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.db.SubjectDao;
import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.util.ImageUtil;

/**
 * 添加/编辑主题项对话框
 */
public class AddSubjectItemDialog extends DialogFragment {
    private static final String TAG = "AddSubjectItemDialog";
    private static final int REQUEST_CODE_PICK_IMAGE = 1001;

    /**
     * 图片移除监听器接口
     */
    interface OnImageRemoveListener {
        void onRemove(String imagePath);
    }

    private static final String ARG_SUBJECT_ID = "subject_id";
    private static final String ARG_ITEM_ID = "item_id";
    private static final String ARG_IS_EDIT = "is_edit";

    private Button btnSelectLink;
    private TextView textSelectedLink;
    private Button btnAddImage;
    private TextView textImageCount;
    private RecyclerView recyclerImages;
    private TextInputEditText editRemark;

    private long subjectId;
    private SubjectItem editItem; // 编辑模式时的主题项
    private LinkItem selectedLink;
    private List<String> selectedImagePaths = new ArrayList<>();
    private ImagePreviewAdapter imageAdapter;
    private SubjectDao subjectDao;
    private LinkDao linkDao;
    private OnSubjectItemSavedListener listener;

    public interface OnSubjectItemSavedListener {
        void onSubjectItemSaved(SubjectItem item);
    }

    /**
     * 创建新主题项对话框
     */
    public static AddSubjectItemDialog newInstance(long subjectId) {
        AddSubjectItemDialog dialog = new AddSubjectItemDialog();
        Bundle args = new Bundle();
        args.putLong(ARG_SUBJECT_ID, subjectId);
        args.putBoolean(ARG_IS_EDIT, false);
        dialog.setArguments(args);
        return dialog;
    }

    /**
     * 编辑主题项对话框
     */
    public static AddSubjectItemDialog newInstance(long subjectId, SubjectItem item) {
        AddSubjectItemDialog dialog = new AddSubjectItemDialog();
        Bundle args = new Bundle();
        args.putLong(ARG_SUBJECT_ID, subjectId);
        args.putLong(ARG_ITEM_ID, item.getId());
        args.putBoolean(ARG_IS_EDIT, true);
        dialog.setArguments(args);
        dialog.editItem = item; // 通过静态变量传递
        return dialog;
    }

    public void setOnSubjectItemSavedListener(OnSubjectItemSavedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        subjectId = getArguments().getLong(ARG_SUBJECT_ID);
        boolean isEdit = getArguments().getBoolean(ARG_IS_EDIT, false);

        subjectDao = new SubjectDao(requireContext());
        subjectDao.open();
        linkDao = new LinkDao(requireContext());
        linkDao.open();

        // 如果是编辑模式，加载现有数据
        if (isEdit && editItem != null) {
            long itemId = getArguments().getLong(ARG_ITEM_ID);
            editItem = subjectDao.getSubjectItemById(itemId);
            if (editItem != null) {
                selectedLink = editItem.getLinkId() != null && editItem.getLinkId() > 0
                        ? getLinkById(editItem.getLinkId()) : null;
                selectedImagePaths = new ArrayList<>(editItem.getImages());
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (subjectDao != null) {
            subjectDao.close();
        }
        if (linkDao != null) {
            linkDao.close();
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        boolean isEdit = getArguments().getBoolean(ARG_IS_EDIT, false);

        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_subject_item, null);

        btnSelectLink = view.findViewById(R.id.btn_select_link);
        textSelectedLink = view.findViewById(R.id.text_selected_link);
        btnAddImage = view.findViewById(R.id.btn_add_image);
        textImageCount = view.findViewById(R.id.text_image_count);
        recyclerImages = view.findViewById(R.id.recycler_images);
        editRemark = view.findViewById(R.id.edit_remark);

        // 设置图片预览
        imageAdapter = new ImagePreviewAdapter(selectedImagePaths, path -> {
            selectedImagePaths.remove(path);
            updateImageUI();
        });
        recyclerImages.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        recyclerImages.setAdapter(imageAdapter);

        // 如果是编辑模式，预填充数据
        if (isEdit && editItem != null) {
            if (selectedLink != null) {
                textSelectedLink.setText(selectedLink.getTitle());
                textSelectedLink.setVisibility(View.VISIBLE);
            }
            if (editItem.getRemark() != null) {
                editRemark.setText(editItem.getRemark());
            }
            updateImageUI();
        }

        // 选择链接
        btnSelectLink.setOnClickListener(v -> showSelectLinkDialog());

        // 添加图片
        btnAddImage.setOnClickListener(v -> pickImage());

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle(isEdit ? "编辑主题项" : "添加主题项")
                .setView(view)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (validateAndSave()) {
                    dialog.dismiss();
                }
            });
        });

        return dialog;
    }

    private void showSelectLinkDialog() {
        // 获取所有链接
        List<LinkItem> allLinks = linkDao.getAllLinks();

        if (allLinks.isEmpty()) {
            Toast.makeText(requireContext(), "没有可用的链接", Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建链接列表对话框
        String[] linkTitles = new String[allLinks.size()];
        for (int i = 0; i < allLinks.size(); i++) {
            linkTitles[i] = allLinks.get(i).getTitle() != null ? allLinks.get(i).getTitle() : allLinks.get(i).getUrl();
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("选择链接")
                .setItems(linkTitles, (dialog, which) -> {
                    selectedLink = allLinks.get(which);
                    textSelectedLink.setText(selectedLink.getTitle() != null ? selectedLink.getTitle() : selectedLink.getUrl());
                    textSelectedLink.setVisibility(View.VISIBLE);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void pickImage() {
        int remainingSlots = SubjectItem.MAX_IMAGES - selectedImagePaths.size();
        if (remainingSlots <= 0) {
            Toast.makeText(requireContext(), "最多只能选择 " + SubjectItem.MAX_IMAGES + " 张图片", Toast.LENGTH_SHORT).show();
            return;
        }

        // 使用多选 Intent
        Intent intent = ImageUtil.createGalleryPickerIntentMultiple();
        startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == android.app.Activity.RESULT_OK) {
            if (data != null) {
                // 处理多选图片
                if (data.getClipData() != null) {
                    // 多选模式：从 ClipData 获取多个 URI
                    android.content.ClipData clipData = data.getClipData();
                    int count = clipData.getItemCount();
                    int remainingSlots = SubjectItem.MAX_IMAGES - selectedImagePaths.size();
                    int toAdd = Math.min(count, remainingSlots);
                    
                    if (toAdd < count) {
                        Toast.makeText(requireContext(), 
                            "最多只能选择 " + SubjectItem.MAX_IMAGES + " 张图片，已选择前 " + toAdd + " 张", 
                            Toast.LENGTH_LONG).show();
                    }
                    
                    for (int i = 0; i < toAdd; i++) {
                        Uri imageUri = clipData.getItemAt(i).getUri();
                        saveImageFromUri(imageUri);
                    }
                } else if (data.getData() != null) {
                    // 单选模式：从 getData() 获取单个 URI
                    Uri imageUri = data.getData();
                    saveImageFromUri(imageUri);
                }
            }
        }
    }

    private void saveImageFromUri(Uri imageUri) {
        try {
            // 1. 读取图片
            Bitmap bitmap = ImageUtil.uriToBitmap(requireContext(), imageUri);
            if (bitmap == null) {
                Toast.makeText(requireContext(), "无法读取图片", Toast.LENGTH_SHORT).show();
                return;
            }

            // 2. 压缩图片（限制最大尺寸为 1920px）
            Bitmap resizedBitmap = ImageUtil.resizeBitmap(bitmap, 1920);
            if (resizedBitmap != bitmap) {
                bitmap.recycle();
            }

            // 3. 保存到私有目录
            File imagesDir = new File(requireContext().getFilesDir(), "subject_images");
            if (!imagesDir.exists()) {
                imagesDir.mkdirs();
            }

            String fileName = "subject_image_" + System.currentTimeMillis() + ".jpg";
            File imageFile = new File(imagesDir, fileName);

            FileOutputStream fos = new FileOutputStream(imageFile);
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
            fos.close();

            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle();
            }

            // 4. 添加到列表
            selectedImagePaths.add(imageFile.getAbsolutePath());
            updateImageUI();

            Log.d(TAG, "图片保存成功: " + imageFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "保存图片失败", e);
            Toast.makeText(requireContext(), "保存图片失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateImageUI() {
        int count = selectedImagePaths.size();
        textImageCount.setText("已选择 " + count + " 张图片");
        if (count > 0) {
            recyclerImages.setVisibility(View.VISIBLE);
            if (imageAdapter != null) {
                imageAdapter.setImagePaths(selectedImagePaths);
            }
        } else {
            recyclerImages.setVisibility(View.GONE);
        }
    }

    private boolean validateAndSave() {
        String remark = editRemark.getText() != null ? editRemark.getText().toString().trim() : "";

        // 验证至少包含一项
        boolean hasLink = selectedLink != null;
        boolean hasImages = !selectedImagePaths.isEmpty();
        boolean hasRemark = !TextUtils.isEmpty(remark);

        if (!hasLink && !hasImages && !hasRemark) {
            Toast.makeText(requireContext(), "至少需要包含链接、图片或备注中的一项", Toast.LENGTH_SHORT).show();
            return false;
        }

        // 验证图片数量
        if (selectedImagePaths.size() > SubjectItem.MAX_IMAGES) {
            Toast.makeText(requireContext(), "图片数量不能超过 " + SubjectItem.MAX_IMAGES + " 张", Toast.LENGTH_SHORT).show();
            return false;
        }

        // 创建或更新主题项
        SubjectItem item;
        if (editItem != null) {
            // 编辑模式
            item = editItem;
        } else {
            // 新建模式
            item = new SubjectItem(subjectId);
            // 计算 orderIndex
            List<SubjectItem> existingItems = subjectDao.getSubjectItemsBySubjectId(subjectId);
            int orderIndex = SubjectUtil.calculateOrderIndex(existingItems, -1);
            item.setOrderIndex(orderIndex);
        }

        // 设置数据
        item.setLinkId(selectedLink != null ? selectedLink.getId() : null);
        item.setRemark(hasRemark ? remark : null);
        item.setImages(selectedImagePaths);

        // 保存到数据库
        if (editItem != null) {
            subjectDao.updateSubjectItem(item);
        } else {
            subjectDao.insertSubjectItem(item);
        }

        if (listener != null) {
            listener.onSubjectItemSaved(item);
        }

        return true;
    }

    private LinkItem getLinkById(long linkId) {
        List<LinkItem> allLinks = linkDao.getAllLinks();
        for (LinkItem link : allLinks) {
            if (link.getId() == linkId) {
                return link;
            }
        }
        return null;
    }

    /**
     * 图片预览适配器
     */
    private class ImagePreviewAdapter extends RecyclerView.Adapter<ImagePreviewAdapter.ImageViewHolder> {
        private List<String> imagePaths;
        private OnImageRemoveListener removeListener;

        ImagePreviewAdapter(List<String> imagePaths, OnImageRemoveListener removeListener) {
            this.imagePaths = new ArrayList<>(imagePaths);
            this.removeListener = removeListener;
        }

        void setImagePaths(List<String> imagePaths) {
            this.imagePaths = new ArrayList<>(imagePaths);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // 创建容器布局（FrameLayout），包含图片和删除按钮
            android.widget.FrameLayout container = new android.widget.FrameLayout(parent.getContext());
            container.setLayoutParams(new ViewGroup.LayoutParams(200, 200));
            container.setPadding(8, 8, 8, 8);
            
            // 创建图片视图
            android.widget.ImageView imageView = new android.widget.ImageView(parent.getContext());
            android.widget.FrameLayout.LayoutParams imageParams = new android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            imageView.setLayoutParams(imageParams);
            imageView.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            container.addView(imageView);
            
            // 创建删除按钮
            android.widget.ImageButton deleteButton = new android.widget.ImageButton(parent.getContext());
            android.widget.FrameLayout.LayoutParams deleteParams = new android.widget.FrameLayout.LayoutParams(
                    36, 36);
            deleteParams.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
            deleteParams.setMargins(0, 0, 4, 4);
            deleteButton.setLayoutParams(deleteParams);
            // 使用系统删除图标
            deleteButton.setImageResource(android.R.drawable.ic_menu_delete);
            deleteButton.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            deleteButton.setPadding(6, 6, 6, 6);
            // 设置半透明黑色圆形背景
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            bg.setColor(0xCC000000); // 半透明黑色
            deleteButton.setBackground(bg);
            deleteButton.setColorFilter(android.graphics.Color.WHITE); // 图标设为白色
            container.addView(deleteButton);
            
            return new ImageViewHolder(container, imageView, deleteButton);
        }

        @Override
        public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
            String imagePath = imagePaths.get(position);
            holder.bind(imagePath);
        }

        @Override
        public int getItemCount() {
            return imagePaths.size();
        }

        class ImageViewHolder extends RecyclerView.ViewHolder {
            private android.widget.ImageView imageView;
            private android.widget.ImageButton deleteButton;

            ImageViewHolder(@NonNull View itemView, android.widget.ImageView imageView, android.widget.ImageButton deleteButton) {
                super(itemView);
                this.imageView = imageView;
                this.deleteButton = deleteButton;
                
                // 删除按钮点击事件
                deleteButton.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && removeListener != null) {
                        removeListener.onRemove(imagePaths.get(position));
                    }
                });
                
                // 保留长按删除功能（备用）
                itemView.setOnLongClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && removeListener != null) {
                        removeListener.onRemove(imagePaths.get(position));
                        return true;
                    }
                    return false;
                });
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

