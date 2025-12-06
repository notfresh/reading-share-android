package person.notfresh.readingshare.util;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import person.notfresh.readingshare.model.LinkItem;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 导入工具类，用于从 CSV 或 JSON 文件导入链接数据
 */
public class ImportUtil {
    
    /**
     * 导入结果类
     */
    public static class ImportResult {
        public final String format; // "CSV" 或 "JSON"
        public final int successCount;
        public final int errorCount;
        public final List<LinkItem> items;
        public final List<String> errors;
        
        public ImportResult(String format, int successCount, int errorCount, 
                          List<LinkItem> items, List<String> errors) {
            this.format = format;
            this.successCount = successCount;
            this.errorCount = errorCount;
            this.items = items;
            this.errors = errors;
        }
        
        public boolean isSuccess() {
            return errorCount == 0;
        }
    }
    
    /**
     * 检测文件格式（扩展名优先，MIME 类型后备）
     * @param context Context
     * @param uri 文件 URI
     * @return "CSV" 或 "JSON"，如果无法确定则返回 null
     */
    public static String detectFileFormat(Context context, Uri uri) {
        String path = uri.getPath();
        
        // 1. 优先检查文件路径扩展名
        if (path != null) {
            if (path.endsWith(".csv")) {
                return "CSV";
            } else if (path.endsWith(".json")) {
                return "JSON";
            }
        }
        
        // 2. 如果扩展名不可用，使用 MIME 类型作为后备
        // 适用于云存储 URI（如 Google Drive）可能没有明确的路径扩展名
        ContentResolver contentResolver = context.getContentResolver();
        String mimeType = contentResolver.getType(uri);
        
        if (mimeType != null) {
            // 支持多种 CSV MIME 类型变体
            if (mimeType.equals("text/csv") || 
                mimeType.equals("text/comma-separated-values") ||
                mimeType.equals("application/csv")) {
                return "CSV";
            } else if (mimeType.equals("application/json")) {
                return "JSON";
            }
        }
        
        // 无法确定文件类型
        return null;
    }
    
    /**
     * 从 URI 导入文件（自动检测格式）
     * @param context Context
     * @param uri 文件 URI
     * @return ImportResult 导入结果
     */
    public static ImportResult importFromUri(Context context, Uri uri) {
        String format = detectFileFormat(context, uri);
        
        if (format == null) {
            List<String> errors = new ArrayList<>();
            errors.add("无法确定文件类型，请确保文件是 CSV 或 JSON 格式");
            return new ImportResult(null, 0, 1, new ArrayList<>(), errors);
        }
        
        if ("CSV".equals(format)) {
            return importFromCsv(context, uri);
        } else if ("JSON".equals(format)) {
            return importFromJson(context, uri);
        } else {
            List<String> errors = new ArrayList<>();
            errors.add("不支持的文件格式: " + format);
            return new ImportResult(format, 0, 1, new ArrayList<>(), errors);
        }
    }
    
    /**
     * 从 CSV 文件导入
     * @param context Context
     * @param uri CSV 文件 URI
     * @return ImportResult 导入结果
     */
    public static ImportResult importFromCsv(Context context, Uri uri) {
        List<LinkItem> items = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                errors.add("无法打开文件");
                return new ImportResult("CSV", 0, 1, items, errors);
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            
            // 跳过标题行
            reader.readLine();
            
            while ((line = reader.readLine()) != null) {
                try {
                    // 使用正则表达式来处理逗号分隔的问题
                    String[] columns = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                    // 检查列数，允许最少3列（标签可以为空）
                    if (columns.length >= 3) {
                        // 移除双引号
                        String title = columns[0].trim().replaceFirst("^\"|\"$", "");
                        String url = columns[1].trim().replaceFirst("^\"|\"$", "");
                        String dateStr = columns[2].trim().replaceFirst("^\"|\"$", "");
                        
                        Date date = null;
                        String[] dateFormats = {
                            "yyyy-MM-dd HH:mm:ss",
                            "yyyy/MM/dd HH:mm",
                            "yyyy-MM-dd HH:mm", 
                            "yyyy/MM/dd"
                        };
                        
                        for (String format : dateFormats) {
                            try {
                                SimpleDateFormat sdf = new SimpleDateFormat(format);
                                date = sdf.parse(dateStr);
                                break;
                            } catch (ParseException e) {
                                continue;
                            }
                        }
                        
                        if (date == null) {
                            throw new ParseException("无法解析日期: " + dateStr, 0);
                        }
                        long timestamp = date.getTime();
                        
                        // 处理标签列
                        List<String> tags = new ArrayList<>();
                        if (columns.length >= 4) {
                            String tagsString = columns[3].trim();
                            if (!tagsString.isEmpty()) {
                                if (tagsString.startsWith("\"") && tagsString.endsWith("\"")) {
                                    // 如果标签字符串被双引号包围，说明可能包含多个标签
                                    // 去掉首尾的双引号
                                    tagsString = tagsString.substring(1, tagsString.length() - 1);
                                    // 按逗号分割，并处理每个标签
                                    String[] tagArray = tagsString.split(",");
                                    for (String tag : tagArray) {
                                        String cleanTag = tag.trim();
                                        if (!cleanTag.isEmpty()) {
                                            tags.add(cleanTag);
                                        }
                                    }
                                } else {
                                    // 如果没有双引号包围，说明只有一个标签
                                    tags.add(tagsString);
                                }
                            }
                        }
                        
                        int clickCount = 0;
                        if (columns.length >= 5) {
                            try {
                                clickCount = Integer.parseInt(columns[4].trim());
                            } catch (NumberFormatException e) {
                                Log.w("ImportUtil", "点击数转换失败: " + columns[4], e);
                                clickCount = 0;
                            }
                        }
                        
                        String summary = "";    
                        if (columns.length >= 6) {
                            summary = columns[5].trim().replaceFirst("^\"|\"$", "");
                        }
                        
                        LinkItem newLink = new LinkItem(title, url, "imported", "", "");
                        newLink.setTimestamp(timestamp);
                        newLink.setTags(tags);
                        newLink.setClickCount(clickCount);
                        newLink.setSummary(summary);
                        
                        items.add(newLink);
                        Log.d("ImportUtil", "解析 CSV 行: " + title + ", 标签数量=" + tags.size());
                    }
                } catch (Exception e) {
                    String errorMsg = "处理行时出错: " + line + ", 错误: " + e.getMessage();
                    Log.e("ImportUtil", errorMsg, e);
                    errors.add(errorMsg);
                }
            }
            reader.close();
            
            return new ImportResult("CSV", items.size(), errors.size(), items, errors);
        } catch (Exception e) {
            String errorMsg = "导入 CSV 失败: " + e.getMessage();
            Log.e("ImportUtil", errorMsg, e);
            errors.add(errorMsg);
            return new ImportResult("CSV", items.size(), errors.size(), items, errors);
        }
    }
    
    /**
     * 从 JSON 文件导入
     * @param context Context
     * @param uri JSON 文件 URI
     * @return ImportResult 导入结果
     */
    public static ImportResult importFromJson(Context context, Uri uri) {
        List<LinkItem> items = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                errors.add("无法打开文件");
                return new ImportResult("JSON", 0, 1, items, errors);
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder jsonString = new StringBuilder();
            String line;
            
            // 读取整个 JSON 文件
            while ((line = reader.readLine()) != null) {
                jsonString.append(line);
            }
            reader.close();
            
            // 解析 JSON
            JSONArray jsonArray = new JSONArray(jsonString.toString());
            
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    
                    // 读取必需字段
                    String title = jsonObject.optString("title", "");
                    String url = jsonObject.optString("url", "");
                    
                    if (title.isEmpty() || url.isEmpty()) {
                        String errorMsg = "跳过无效项（缺少 title 或 url）: " + jsonObject.toString();
                        Log.w("ImportUtil", errorMsg);
                        errors.add(errorMsg);
                        continue;
                    }
                    
                    // 读取可选字段
                    long timestamp = jsonObject.optLong("timestamp", System.currentTimeMillis());
                    int clickCount = jsonObject.optInt("clickCount", 0);
                    String summary = jsonObject.optString("summary", "");
                    
                    // 读取标签
                    List<String> tags = new ArrayList<>();
                    if (jsonObject.has("tags")) {
                        JSONArray tagsArray = jsonObject.optJSONArray("tags");
                        if (tagsArray != null) {
                            for (int j = 0; j < tagsArray.length(); j++) {
                                String tag = tagsArray.optString(j, "");
                                if (!tag.isEmpty()) {
                                    tags.add(tag);
                                }
                            }
                        }
                    }
                    
                    // 创建 LinkItem
                    LinkItem newLink = new LinkItem(title, url, "imported", "", "");
                    newLink.setTimestamp(timestamp);
                    newLink.setTags(tags);
                    newLink.setClickCount(clickCount);
                    newLink.setSummary(summary);
                    
                    items.add(newLink);
                    Log.d("ImportUtil", "解析 JSON 对象: " + title + ", 标签数量=" + tags.size());
                } catch (JSONException e) {
                    String errorMsg = "解析 JSON 对象失败: " + e.getMessage();
                    Log.e("ImportUtil", errorMsg, e);
                    errors.add(errorMsg);
                }
            }
            
            return new ImportResult("JSON", items.size(), errors.size(), items, errors);
        } catch (JSONException e) {
            String errorMsg = "JSON 解析失败: " + e.getMessage();
            Log.e("ImportUtil", errorMsg, e);
            errors.add(errorMsg);
            return new ImportResult("JSON", items.size(), errors.size(), items, errors);
        } catch (Exception e) {
            String errorMsg = "导入 JSON 失败: " + e.getMessage();
            Log.e("ImportUtil", errorMsg, e);
            errors.add(errorMsg);
            return new ImportResult("JSON", items.size(), errors.size(), items, errors);
        }
    }
}

