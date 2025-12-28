package person.notfresh.readingshare.ui.subject;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.viewpager.widget.ViewPager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import person.notfresh.readingshare.R;

/**
 * 图片预览对话框
 * 支持全屏查看和滑动切换
 */
public class ImagePreviewDialog extends DialogFragment {
    private static final String ARG_IMAGE_PATHS = "image_paths";
    private static final String ARG_CURRENT_INDEX = "current_index";

    private ViewPager viewPager;
    private TextView textImageIndex;
    private List<String> imagePaths;
    private int currentIndex;

    /**
     * 创建图片预览对话框
     */
    public static ImagePreviewDialog newInstance(List<String> imagePaths, int currentIndex) {
        ImagePreviewDialog dialog = new ImagePreviewDialog();
        Bundle args = new Bundle();
        args.putStringArrayList(ARG_IMAGE_PATHS, new ArrayList<>(imagePaths));
        args.putInt(ARG_CURRENT_INDEX, currentIndex);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            imagePaths = getArguments().getStringArrayList(ARG_IMAGE_PATHS);
            currentIndex = getArguments().getInt(ARG_CURRENT_INDEX, 0);
        }
        if (imagePaths == null) {
            imagePaths = new ArrayList<>();
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        // 设置全屏
        Window window = dialog.getWindow();
        if (window != null) {
            window.requestFeature(Window.FEATURE_NO_TITLE);
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                            @Nullable ViewGroup container,
                            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_image_preview, container, false);

        viewPager = view.findViewById(R.id.view_pager);
        textImageIndex = view.findViewById(R.id.text_image_index);

        // 设置 ViewPager 适配器
        ImagePagerAdapter adapter = new ImagePagerAdapter(imagePaths);
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(currentIndex, false);

        // 显示图片索引（如果有多张图片）
        if (imagePaths.size() > 1) {
            textImageIndex.setVisibility(View.VISIBLE);
            updateImageIndex();
            viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
                @Override
                public void onPageSelected(int position) {
                    super.onPageSelected(position);
                    currentIndex = position;
                    updateImageIndex();
                }
            });
        }

        // 点击关闭：在 ViewPager 和根视图上都设置点击监听
        // 因为 ViewPager 占据了整个空间，会拦截点击事件
        viewPager.setOnClickListener(v -> dismiss());
        view.setOnClickListener(v -> dismiss());

        return view;
    }

    private void updateImageIndex() {
        if (imagePaths.size() > 1) {
            textImageIndex.setText((currentIndex + 1) + " / " + imagePaths.size());
        }
    }

    /**
     * ViewPager 适配器
     */
    private static class ImagePagerAdapter extends androidx.viewpager.widget.PagerAdapter {
        private List<String> imagePaths;

        ImagePagerAdapter(List<String> imagePaths) {
            this.imagePaths = imagePaths != null ? new ArrayList<>(imagePaths) : new ArrayList<>();
        }

        @Override
        public int getCount() {
            return imagePaths.size();
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            ImageView imageView = new ImageView(container.getContext());
            imageView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            // 设置 ImageView 可点击，这样点击图片时也会关闭对话框
            imageView.setClickable(true);
            imageView.setFocusable(true);
            
            String imagePath = imagePaths.get(position);
            bindImage(imageView, imagePath);
            
            container.addView(imageView);
            return imageView;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            container.removeView((View) object);
        }

        private void bindImage(ImageView imageView, String imagePath) {

            if (imagePath != null) {
                File imageFile = new File(imagePath);
                if (imageFile.exists()) {
                    // 加载图片（可能需要缩放以适应屏幕）
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(imagePath, options);
                    
                    // 计算缩放比例
                    int imageWidth = options.outWidth;
                    int imageHeight = options.outHeight;
                    int screenWidth = imageView.getContext().getResources().getDisplayMetrics().widthPixels;
                    int screenHeight = imageView.getContext().getResources().getDisplayMetrics().heightPixels;
                    
                    int scale = 1;
                    if (imageWidth > screenWidth || imageHeight > screenHeight) {
                        int scaleX = imageWidth / screenWidth;
                        int scaleY = imageHeight / screenHeight;
                        scale = Math.max(scaleX, scaleY);
                    }
                    
                    options.inJustDecodeBounds = false;
                    options.inSampleSize = scale;
                    
                    Bitmap bitmap = BitmapFactory.decodeFile(imagePath, options);
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap);
                    }
                }
            }
        }
    }
}

