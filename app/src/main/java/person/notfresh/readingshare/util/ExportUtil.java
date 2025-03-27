package person.notfresh.readingshare.util;

import android.content.Context;
import android.os.Environment;
import android.text.TextUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import person.notfresh.readingshare.model.LinkItem;
import java.io.IOException;
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
        FileWriter writer = new FileWriter(file);
        writer.write(jsonArray.toString(4)); // 缩进4个空格，使JSON更易读
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
        FileWriter writer = new FileWriter(file);
        
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
        String fileName = "links_" + getCurrentTime() + ".json";
        // 调用新方法
        return exportToJson(context, links, fileName);
    }

    /**
     * 原始的导出到CSV方法（向后兼容）
     */
    public static String exportToCsv(Context context, List<LinkItem> links) throws IOException {
        // 生成默认文件名
        String fileName = "links_" + getCurrentTime() + ".csv";
        // 调用新方法
        return exportToCsv(context, links, fileName);
    }

    private static String getCurrentTime() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
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