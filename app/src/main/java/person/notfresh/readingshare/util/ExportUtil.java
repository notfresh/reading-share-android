package person.notfresh.readingshare.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import person.notfresh.readingshare.model.LinkItem;
import android.util.Log;
import org.json.JSONException;

public class ExportUtil {
    
    /**
     * 导出到JSON文件，支持自定义文件名
     */
    public static String exportToJson(Context context, List<LinkItem> links, String fileName) throws IOException, JSONException {
        JSONArray jsonArray = new JSONArray();
        
        for (LinkItem link : links) {
            try {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("title", link.getTitle());
                jsonObject.put("url", link.getUrl());
                jsonObject.put("tags", new JSONArray(link.getTags()));
                jsonArray.put(jsonObject);
            } catch (JSONException e) {
                Log.e("ExportUtil", "Error creating JSON object", e);
            }
        }
        
        // 确保文件名有.json后缀
        if (!fileName.toLowerCase().endsWith(".json")) {
            fileName += ".json";
        }
        
        File exportDir = new File(context.getExternalFilesDir(null), "exports");
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }
        
        File file = new File(exportDir, fileName);
        // 使用 UTF-8 编码明确指定
        OutputStreamWriter writer = new OutputStreamWriter(
            new FileOutputStream(file), StandardCharsets.UTF_8);
        // 将 JSONObject 转义的反斜杠还原（\/ -> /），使 JSON 更易读
        String jsonString = jsonArray.toString(4).replace("\\/", "/");
        writer.write(jsonString);
        writer.flush();
        writer.close();
        
        return file.getAbsolutePath();
    }

    /**
     * 导出到CSV文件，支持自定义文件名
     */
    public static String exportToCsv(Context context, List<LinkItem> links, String fileName) throws IOException {
        // 确保文件名有.csv后缀
        if (!fileName.toLowerCase().endsWith(".csv")) {
            fileName += ".csv";
        }
        
        File exportDir = new File(context.getExternalFilesDir(null), "exports");
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }
        
        File file = new File(exportDir, fileName);
        // 使用 UTF-8 编码明确指定
        OutputStreamWriter writer = new OutputStreamWriter(
            new FileOutputStream(file), StandardCharsets.UTF_8);
        
        // 创建CSV内容
        StringBuilder csv = new StringBuilder();
        // 写入CSV标题行
        csv.append("标题,链接,时间,标签,阅读次数,摘要\n");
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        
        // 写入每一条链接
        for (LinkItem link : links) {
            String title = escapeCSV(link.getTitle());
            String url = escapeCSV(link.getUrl());
            String date = sdf.format(new Date(link.getTimestamp()));
            String tags = escapeCSV(TextUtils.join(",", link.getTags()));
            String clickCount = String.valueOf(link.getClickCount());
            String summary = escapeCSV(link.getSummary());
            
            csv.append(String.format("%s,%s,%s,%s,%s,%s\n",
                    title, url, date, tags, clickCount, summary));
        }
        
        writer.write(csv.toString());
        writer.flush();
        writer.close();
        
        return file.getAbsolutePath();
    }

    /**
     * 原始的导出到JSON方法（向后兼容）
     */
    public static String exportToJson(Context context, List<LinkItem> links) throws IOException, JSONException {
        // 生成默认文件名
        String fileName = "links_" + getCurrentTime() + "_readshare.json";
        // 调用新方法
        return exportToJson(context, links, fileName);
    }

    /**
     * 原始的导出到CSV方法（向后兼容）
     */
    public static String exportToCsv(Context context, List<LinkItem> links) throws IOException {
        // 生成默认文件名
        String fileName = "links_" + getCurrentTime() + "_readshare.csv";
        // 调用新方法
        return exportToCsv(context, links, fileName);
    }

    /**
     * 获取当前时间戳字符串（用于文件名）
     * @return 格式化的时间字符串，如 "20250105_143022"
     */
    public static String getCurrentTime() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
    }

    /**
     * 导出到公共 Documents 目录（Android 10+ 使用 MediaStore，旧版本使用传统方式）
     * @param context Context
     * @param links 要导出的链接列表
     * @param isJson 是否为 JSON 格式（false 为 CSV）
     * @return 保存的文件 URI
     * @throws IOException 文件操作异常
     * @throws JSONException JSON 解析异常
     */
    public static Uri exportToPublicDirectory(Context context, List<LinkItem> links, 
                                             boolean isJson) throws IOException, JSONException {
        // 使用默认文件名（已包含扩展名）
        String defaultFileName = isJson 
            ? "links_" + getCurrentTime() + "_readshare.json.txt"
            : "links_" + getCurrentTime() + "_readshare.csv";
        return exportToPublicDirectory(context, links, isJson, defaultFileName);
    }
    
    /**
     * 导出到公共 Documents 目录（支持自定义文件名）
     * 注意：文件名应该已经在调用前处理好了（添加扩展名等），这里直接使用
     * @param context Context
     * @param links 要导出的链接列表
     * @param isJson 是否为 JSON 格式（false 为 CSV）
     * @param fileName 已处理好的文件名（包含扩展名）
     * @return 保存的文件 URI
     * @throws IOException 文件操作异常
     * @throws JSONException JSON 解析异常
     */
    public static Uri exportToPublicDirectory(Context context, List<LinkItem> links, 
                                             boolean isJson, String fileName) throws IOException, JSONException {
        String mimeType = isJson ? "text/plain" : "text/csv";
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用 MediaStore API
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS);
            
            ContentResolver resolver = context.getContentResolver();
            Uri fileUri = resolver.insert(MediaStore.Files.getContentUri("external"), values);
            
            if (fileUri != null) {
                OutputStream outputStream = resolver.openOutputStream(fileUri);
                if (outputStream != null) {
                    writeDataToStream(outputStream, links, isJson);
                    outputStream.close();
                    return fileUri;
                }
            }
            throw new IOException("无法创建文件");
        } else {
            // Android 9 及以下使用传统文件存储
            File documentsFolder = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS);
            if (!documentsFolder.exists()) {
                documentsFolder.mkdirs();
            }
            
            File outputFile = new File(documentsFolder, fileName);
            FileOutputStream fos = new FileOutputStream(outputFile);
            writeDataToStream(fos, links, isJson);
            fos.close();
            
            return Uri.fromFile(outputFile);
        }
    }
    
    /**
     * 将数据写入输出流
     * @param outputStream 输出流
     * @param links 链接列表
     * @param isJson 是否为 JSON 格式
     * @throws IOException 文件操作异常
     * @throws JSONException JSON 解析异常
     */
    private static void writeDataToStream(OutputStream outputStream, List<LinkItem> links, 
                                         boolean isJson) throws IOException, JSONException {
        OutputStreamWriter writer = new OutputStreamWriter(
            outputStream, StandardCharsets.UTF_8);
        
        if (isJson) {
            // 写入 JSON 数据
            JSONArray jsonArray = new JSONArray();
            for (LinkItem link : links) {
                try {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("title", link.getTitle());
                    jsonObject.put("url", link.getUrl());
                    jsonObject.put("tags", new JSONArray(link.getTags()));
                    jsonArray.put(jsonObject);
                } catch (JSONException e) {
                    Log.e("ExportUtil", "Error creating JSON object", e);
                }
            }
            // 将 JSONObject 转义的反斜杠还原（\/ -> /），使 JSON 更易读
            String jsonString = jsonArray.toString(4).replace("\\/", "/");
            writer.write(jsonString);
        } else {
            // 写入 CSV 数据
            StringBuilder csv = new StringBuilder();
            csv.append("标题,链接,时间,标签,阅读次数,摘要\n");
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            
            for (LinkItem link : links) {
                String title = escapeCSV(link.getTitle());
                String url = escapeCSV(link.getUrl());
                String date = sdf.format(new Date(link.getTimestamp()));
                String tags = escapeCSV(TextUtils.join(",", link.getTags()));
                String clickCount = String.valueOf(link.getClickCount());
                String summary = escapeCSV(link.getSummary());
                
                csv.append(String.format("%s,%s,%s,%s,%s,%s\n",
                        title, url, date, tags, clickCount, summary));
            }
            writer.write(csv.toString());
        }
        
        writer.flush();
        writer.close();
    }

    private static String escapeCSV(String value) {
        if (value == null) return "";
        value = value.replace("\"", "\"\""); // 转义双引号
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            value = "\"" + value + "\""; // 如果包含逗号或换行，用双引号包围
        }
        return value;
    }
} 