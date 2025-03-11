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

public class ExportUtil {
    
    public static String exportToJson(Context context, List<LinkItem> links) throws Exception {
        JSONArray jsonArray = new JSONArray();
        for (LinkItem link : links) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("title", link.getTitle());
            jsonObject.put("url", link.getUrl());
            jsonObject.put("timestamp", link.getTimestamp());
            jsonObject.put("tags", TextUtils.join(",", link.getTags()));
            jsonArray.put(jsonObject);
        }

        String fileName = "links_" + getCurrentTime() + ".json";
        File file = new File(context.getExternalFilesDir(null), fileName);
        FileWriter writer = new FileWriter(file);
        writer.write(jsonArray.toString(4)); // 缩进4个空格
        writer.close();
        
        return file.getAbsolutePath();
    }

    public static String exportToCsv(Context context, List<LinkItem> links) throws Exception {
        StringBuilder csv = new StringBuilder();
        // 修改CSV头，移除来源字段
        csv.append("标题,链接,时间,标签\n");
        
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

        String fileName = "links_" + getCurrentTime() + ".csv";
        File file = new File(context.getExternalFilesDir(null), fileName);
        FileWriter writer = new FileWriter(file);
        writer.write(csv.toString());
        writer.close();
        
        return file.getAbsolutePath();
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