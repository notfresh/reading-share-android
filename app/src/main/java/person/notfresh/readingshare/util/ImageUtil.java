package person.notfresh.readingshare.util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.io.InputStream;

/**
 * 图片处理工具类
 * 提供图片选择、转换、缩放等通用功能
 */
public class ImageUtil {

    /**
     * 创建相册选择 Intent（单选）
     * @return 相册选择 Intent
     */
    public static Intent createGalleryPickerIntent() {
        return new Intent(Intent.ACTION_PICK, 
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
    }

    /**
     * 创建相册选择 Intent（多选）
     * @return 支持多选的相册选择 Intent
     */
    public static Intent createGalleryPickerIntentMultiple() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        return intent;
    }

    /**
     * 从 URI 转换为 Bitmap
     * 支持 content:// 和 file:// 协议
     * @param context 上下文
     * @param uri 图片 URI
     * @return Bitmap 对象，如果转换失败返回 null
     */
    public static Bitmap uriToBitmap(Context context, Uri uri) {
        if (context == null || uri == null) {
            Log.w("ImageUtil", "Context or URI is null");
            return null;
        }

        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Log.w("ImageUtil", "Failed to open input stream for URI: " + uri);
                return null;
            }

            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            if (bitmap != null) {
                Log.d("ImageUtil", "Successfully converted URI to Bitmap, size: " + 
                      bitmap.getWidth() + "x" + bitmap.getHeight());
            } else {
                Log.w("ImageUtil", "Failed to decode bitmap from URI: " + uri);
            }

            return bitmap;
        } catch (Exception e) {
            Log.e("ImageUtil", "Error converting URI to Bitmap: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 缩放图片，保持宽高比
     * @param image 原始图片
     * @param maxSize 最大尺寸（宽度或高度）
     * @return 缩放后的图片
     */
    public static Bitmap resizeBitmap(Bitmap image, int maxSize) {
        if (image == null) {
            Log.w("ImageUtil", "Image is null");
            return null;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        // 如果图片已经小于等于最大尺寸，直接返回
        if (width <= maxSize && height <= maxSize) {
            return image;
        }

        float bitmapRatio = (float) width / (float) height;
        int newWidth, newHeight;

        if (bitmapRatio > 1) {
            // 宽度大于高度
            newWidth = maxSize;
            newHeight = (int) (newWidth / bitmapRatio);
        } else {
            // 高度大于等于宽度
            newHeight = maxSize;
            newWidth = (int) (newHeight * bitmapRatio);
        }

        Log.d("ImageUtil", "Resizing bitmap from " + width + "x" + height + 
              " to " + newWidth + "x" + newHeight);
        
        return Bitmap.createScaledBitmap(image, newWidth, newHeight, true);
    }

    /**
     * 将图片缩放为正方形（用于快捷方式图标）
     * 采用居中裁剪的方式，保留图片中心部分
     * @param image 原始图片
     * @param size 目标尺寸（正方形边长）
     * @return 缩放后的正方形图片
     */
    public static Bitmap resizeToSquare(Bitmap image, int size) {
        if (image == null) {
            Log.w("ImageUtil", "Image is null");
            return null;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        // 如果已经是目标尺寸的正方形，直接返回
        if (width == size && height == size) {
            return image;
        }

        // 计算缩放比例，使较短的边等于目标尺寸
        float scale = (float) size / Math.min(width, height);
        int scaledWidth = (int) (width * scale);
        int scaledHeight = (int) (height * scale);

        // 先缩放图片
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(image, scaledWidth, scaledHeight, true);

        // 如果缩放后不是正方形，进行居中裁剪
        if (scaledWidth != scaledHeight) {
            int x = (scaledWidth - size) / 2;
            int y = (scaledHeight - size) / 2;
            
            // 确保裁剪区域有效
            x = Math.max(0, x);
            y = Math.max(0, y);
            
            Bitmap squareBitmap = Bitmap.createBitmap(scaledBitmap, x, y, size, size);
            
            // 释放缩放后的图片（如果不是原图）
            if (scaledBitmap != image) {
                scaledBitmap.recycle();
            }
            
            Log.d("ImageUtil", "Resized to square: " + size + "x" + size);
            return squareBitmap;
        }

        Log.d("ImageUtil", "Resized to square: " + size + "x" + size);
        return scaledBitmap;
    }

    /**
     * 将图片缩放为正方形（用于快捷方式图标）
     * 如果图片已经是正方形，直接缩放；否则先缩放再居中裁剪
     * @param image 原始图片
     * @param size 目标尺寸（正方形边长）
     * @return 缩放后的正方形图片
     */
    public static Bitmap resizeToSquareForShortcut(Bitmap image) {
        return resizeToSquare(image, 256); // 快捷方式图标标准尺寸
    }
}

