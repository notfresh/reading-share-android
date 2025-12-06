package person.notfresh.readingshare.util;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.widget.Toast;
import android.webkit.WebView;
import android.util.Log;
import android.content.ClipData;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import androidx.core.content.FileProvider;
import org.json.JSONException;

import person.notfresh.readingshare.model.LinkItem;

/**
 * 分享相关的通用工具。
 * 仅负责构建分享 Intent 和调起系统分享面板；
 * 文件导出委托给 ExportUtil。
 */
public class ShareUtil {

    // 回调：用于从对话框返回用户选项（如删除与否）
    public interface OptionsCallback {
        void onConfirmed(boolean deleteAfterShare);
    }

    // 对外入口（常用优先）
    /**
     * 将单条链接导出为 JSON/CSV 文件后，通过文件方式分享。
     * 先保存到公共 Documents 目录，然后再分享。
     * @param isJson true 导出 JSON；false 导出 CSV
     */
    public static void shareLinksAsFile(Context context, List<LinkItem> items, boolean isJson, String fileName, boolean ifSaveOnly) {
        try {
            Uri fileUri = null;
            if(ifSaveOnly){
                fileUri = ExportUtil.exportToPublicDirectory(context, items, isJson, fileName);
                // 显示保存成功提示
                String format = isJson ? "JSON" : "CSV";
                Toast.makeText(context, format + " 文件已保存到 Documents 目录", Toast.LENGTH_SHORT).show();
                return;
            } else {
                // 导出文件到应用目录
                String filePath = isJson 
                    ? ExportUtil.exportToJson(context, items, fileName)
                    : ExportUtil.exportToCsv(context, items, fileName);
                
                // 使用 FileProvider 生成 URI（而不是 file:// URI）
                File file = new File(filePath);
                if (!file.exists()) {
                    throw new IOException("文件创建失败，文件不存在: " + filePath);
                }
                
                try {
                    // 使用 FileProvider 获取 URI
                    fileUri = FileProvider.getUriForFile(
                        context, 
                        context.getPackageName() + ".provider", 
                        file
                    );
                    Log.d("ShareUtil", "文件URI生成成功: " + fileUri);
                } catch (IllegalArgumentException e) {
                    Log.e("ShareUtil", "FileProvider URI生成失败", e);
                    throw new IOException("无法生成文件URI，请检查FileProvider配置: " + e.getMessage());
                }
            }
            
            // 构建分享 Intent
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(isJson ? "application/json" : "text/csv");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, fileName);
            
            // 授予临时读取权限
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            // 使用 ClipData 传递文件信息（更好的兼容性）
            ClipData clipData = ClipData.newUri(context.getContentResolver(), fileName, fileUri);
            shareIntent.setClipData(clipData);

            // 创建分享选择器
            Intent chooser = Intent.createChooser(shareIntent, "分享文件");
            excludeSelfFromChooser(context, chooser);
            
            // 授予选择器临时权限
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            context.startActivity(chooser);
            Log.d("ShareUtil", "分享选择器已启动");
            
        } catch (IOException e) {
            Log.e("ShareUtil", "文件操作失败", e);
            String errorMsg = "文件操作失败: " + e.getMessage();
            if (e.getMessage() != null && e.getMessage().contains("权限")) {
                errorMsg = "文件权限不足，请检查应用权限设置";
            }
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show();
        } catch (JSONException e) {
            Log.e("ShareUtil", "JSON处理失败", e);
            Toast.makeText(context, "JSON格式错误: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (SecurityException e) {
            Log.e("ShareUtil", "权限错误", e);
            Toast.makeText(context, "分享权限不足: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e("ShareUtil", "分享失败", e);
            String errorMsg = "分享失败";
            if (e.getMessage() != null && !e.getMessage().isEmpty()) {
                errorMsg += ": " + e.getMessage();
            } else {
                errorMsg += ": " + e.getClass().getSimpleName();
            }
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 处理文件名：确保有正确的扩展名和格式
     * @param fileName 用户输入的文件名
     * @param isJson 是否为 JSON 格式
     * @return 处理后的文件名
     */
    public static String processFileName(String fileName, boolean isJson) {
        if (fileName == null || fileName.isEmpty()) {
            return fileName;
        }
        
        // 清理非法字符
        fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
        
        String lowerFileName = fileName.toLowerCase();
        if (isJson) {
            if (!lowerFileName.endsWith(".json")) {
                // 没有扩展名或扩展名不对，添加 _readshare.json
                // 先移除可能存在的其他扩展名
                int lastDot = fileName.lastIndexOf('.');
                if (lastDot > 0) {
                    fileName = fileName.substring(0, lastDot);
                }
                fileName += "_readshare.json";
            }
        } else {
            if (!lowerFileName.endsWith(".csv")) {
                // 没有扩展名或扩展名不对，添加 _readshare.csv
                // 先移除可能存在的其他扩展名
                int lastDot = fileName.lastIndexOf('.');
                if (lastDot > 0) {
                    fileName = fileName.substring(0, lastDot);
                }
                fileName += "_readshare.csv";
            }
        }
        
        return fileName;
    }
    
    /**
     * 批量分享链接为文件（带文件名输入对话框）
     * @param isJson true 导出 JSON；false 导出 CSV
     */
    public static void shareLinksAsFileWithDialog(Context context, List<LinkItem> items, boolean isJson) {
        shareLinksAsFileWithDialog(context, items, isJson, null);
    }

    /**
     * 带文件名输入和"是否删除链接"勾选项的对话框，确认后回传用户选择。
     */
    public static void shareLinksAsFileWithDialog(Context context, List<LinkItem> items, boolean isJson, OptionsCallback callback) {
        if (items.isEmpty()) {
            Toast.makeText(context, "请先选择要分享的链接", Toast.LENGTH_SHORT).show();
            return;
        }

        // 自定义视图：输入文件名 + 勾选是否删除
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(context);
        android.widget.LinearLayout container = new android.widget.LinearLayout(context);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = (int) (16 * context.getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, 0);

        final android.widget.EditText input = new android.widget.EditText(context);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        // 生成默认文件名时直接带上后缀
        String baseName = "分享的链接_" + new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(new java.util.Date());
        String defaultFileName = baseName + (isJson ? "_readshare.json" : "_readshare.csv");
        input.setText(defaultFileName);
        input.setSelection(defaultFileName.length());
        input.setHint("文件名");
        container.addView(input);
        final android.widget.CheckBox deleteCheck = new android.widget.CheckBox(context);
        if(callback != null) {
            deleteCheck.setText("分享后删除这些链接");
            container.addView(deleteCheck);
        }
        final android.widget.CheckBox saveOnlyCheck = new android.widget.CheckBox(context);
        if(callback != null) {
            saveOnlyCheck.setText("保存到本地");
            container.addView(saveOnlyCheck);
        }

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context)
                .setTitle("分享设置")
                .setView(container)
                .setPositiveButton("确定", (dialog, which) -> {
                    String fileName = input.getText().toString().trim();
                    if (fileName.isEmpty()) {
                        fileName = defaultFileName;
                    }
                    // 统一处理文件名（确保有正确的扩展名）
                    fileName = processFileName(fileName, isJson);
                    boolean deleteAfter = deleteCheck.isChecked();
                    // 先导出，再删除！！
                    if (callback != null) {
                        callback.onConfirmed(deleteAfter);
                    }

                    shareLinksAsFile(context, items, isJson, fileName, saveOnlyCheck.isChecked());


                })
                .setNegativeButton("取消", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    /**
     * 批量分享链接为文本
     */
    public static void shareLinksAsText(Context context, List<LinkItem> items) {
        if (items.isEmpty()) {
            Toast.makeText(context, "请先选择要分享的链接", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder shareText = new StringBuilder();
        for (LinkItem item : items) {
            // 修正参数顺序：title, remark, url
            shareText.append(buildShareText(item.getTitle(), item.getRemark(), item.getUrl(), false));
        }
        // 批量分享时，只在最后添加一次"来自：ReadingShare"
        shareText.append("来自：ReadingShare\n");

        sharePlainText(context, shareText.toString());
    }

    /**
     * 直接基于原始字段分享（适用于非 LinkItem 调用场景）。
     */
    public static void shareText(Context context, String title, String remark, String url) {
        String text = buildShareText(safeTrim(title), safeTrim(remark), safeTrim(url));
        sharePlainText(context, text);
    }

    // 实际分享与系统交互封装
    private static void sharePlainText(Context context, String text) {
        if (text == null || text.isEmpty()) {
            Toast.makeText(context, "分享内容为空", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, text);
        Intent chooser = Intent.createChooser(shareIntent, "分享到");
        excludeSelfFromChooser(context, chooser);
        context.startActivity(chooser);
    }

    /**
     * 分享单条链接为纯文本（标题/备注/URL）。
     */
    public static void shareLinkAsText(Context context, LinkItem item) {
        String title = safeTrim(item.getTitle());
        String remark = safeTrim(item.getRemark());
        String url = safeTrim(item.getUrl());
        String text = buildShareText(title, remark, url);
        sharePlainText(context, text);
    }

    /**
     * 将单条链接导出为 JSON/CSV 文件后，通过文件方式分享。
     * @param isJson true 导出 JSON；false 导出 CSV
     */
    public static void shareLinkAsFile(Context context, LinkItem item, boolean isJson) {
        List<LinkItem> singleList = Collections.singletonList(item);
        // 生成默认文件名时直接带上后缀
        String baseName = "分享的链接_" + new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(new java.util.Date());
        String fileName = baseName + (isJson ? "_readshare.json" : "_readshare.csv");
        shareLinksAsFile(context, singleList, isJson, fileName, true);
    }

    /**
     * WebView 专用重载：使用当前页面的标题和 URL 分享。
     */
    public static void shareFromWebView(Context context, WebView webView) {
        shareFromWebView(context, webView, null);
    }

    /**
     * WebView 专用重载：使用当前页面的标题和 URL 分享；可选备注可为 null。
     */
    public static void shareFromWebView(Context context, WebView webView, String optionalRemark) {
        if (webView == null) {
            Toast.makeText(context, "分享失败：页面不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        String title = webView.getTitle();
        String url = webView.getUrl();
        Log.d("ShareUtil", "shareFromWebView title=" + title + ", url=" + url);
        shareText(context, title, optionalRemark, url);
    }
     
    //  文本模板与摘要

    /**
     * 构建符合"微信卡片信息结构"的裸文本。
     * 【标题】\n(可选)摘要：...\n链接：...
     * @param includeSource 是否包含"来自：ReadingShare"（批量分享时只在最后加一次）
     */
    private static String buildShareText(String title, String remark, String url, boolean includeSource) {
        String safeTitle = (title == null || title.isEmpty()) ? "新链接" : truncate(title, 60);
        String safeUrl = (url == null) ? "" : url;
        String safeRemark = summarize(remark, 80);

        StringBuilder sb = new StringBuilder();
        sb.append("【").append(safeTitle).append("】\n");
        if (!safeRemark.isEmpty()) {
            sb.append("摘要：").append(safeRemark).append("\n");
        }
        sb.append("链接：").append(safeUrl).append("\n");
        if (includeSource) {
            sb.append("来自：ReadingShare\n");
        }
        return sb.toString();
    }
    
    /**
     * 构建符合"微信卡片信息结构"的裸文本（单条分享，包含来源）。
     * 【标题】\n(可选)摘要：...\n链接：...\n来自：ReadingShare
     */
    private static String buildShareText(String title, String remark, String url) {
        return buildShareText(title, remark, url, true);
    }

    /**
     * 粗糙的摘要工具：去标签（简单方式，假定入参已是纯文本）、压缩空白、长度截断。
     */
    public static String summarize(String text, int maxLength) {
        String cleaned = collapseWhitespace(safeTrim(text));
        if (cleaned.isEmpty()) return "";
        if (maxLength <= 0) return cleaned;
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength) + "..." : cleaned;
    }

    // 3) 文本清洗与通用工具

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String collapseWhitespace(String s) {
        // 将多个空白压缩为单个空格，去除多余换行
        String normalized = s.replaceAll("\r", "");
        // 把多连续空行压成单个换行
        normalized = normalized.replaceAll("\n{2,}", "\n");
        // 行内多空白压缩 (\u000B 为垂直制表符)
        normalized = normalized.replaceAll("[\\t\\u000B\\f]+", " ");
        normalized = normalized.replaceAll(" {2,}", " ");
        return normalized.trim();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (max <= 0) return s;
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }


    /**
     * 可选：尝试在 chooser 中排除当前应用的主要 Activity，避免自我分享目标。
     * 某些系统版本可能忽略该字段，视为最佳努力。
     */
    private static void excludeSelfFromChooser(Context context, Intent chooser) {
        try {
            String pkg = context.getPackageName();
            // 约定 MainActivity 完整类名；如需更通用，可改为动态枚举自身可处理项后过滤。
            ComponentName self = new ComponentName(pkg, pkg + ".MainActivity");
            chooser.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, new ComponentName[]{self});
        } catch (Throwable ignored) {
        }
    }
}


