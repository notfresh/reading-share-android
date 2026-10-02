package person.notfresh.readingshare.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import person.notfresh.readingshare.model.LinkItem;

public class ExportUtil {

    // CSV 列定义 — 单点修改,exportToCsv/writeDataToStream 共用
    private static final String CSV_HEADER =
            "标题,链接,时间,标签,阅读次数,摘要,是否置顶,所属主题";

    // JSON 字段常量 — 单一来源
    private static final String JSON_KEY_TITLE = "title";
    private static final String JSON_KEY_URL = "url";
    private static final String JSON_KEY_TAGS = "tags";
    private static final String JSON_KEY_IS_PINNED = "isPinned";
    private static final String JSON_KEY_SUBJECTS = "subjects";

    /**
     * 导出到JSON文件，支持自定义文件名
     * @param linkIdToSubjectNames linkId → 它归属的 subject 名字列表;null 时 subjects 字段写空数组
     */
    public static String exportToJson(Context context, List<LinkItem> links, String fileName,
                                      Map<Long, List<String>> linkIdToSubjectNames)
            throws IOException, JSONException {
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
        writer.write(buildJson(links, linkIdToSubjectNames));
        writer.flush();
        writer.close();

        return file.getAbsolutePath();
    }

    /**
     * 导出到CSV文件，支持自定义文件名
     * @param linkIdToSubjectNames linkId → 它归属的 subject 名字列表;null 时所属主题列写空
     */
    public static String exportToCsv(Context context, List<LinkItem> links, String fileName,
                                     Map<Long, List<String>> linkIdToSubjectNames) throws IOException {
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
        writer.write(buildCsv(links, linkIdToSubjectNames));
        writer.flush();
        writer.close();

        return file.getAbsolutePath();
    }

    /**
     * 原始的导出到JSON方法（向后兼容）— 无 subject 关联信息
     */
    public static String exportToJson(Context context, List<LinkItem> links) throws IOException, JSONException {
        // 生成默认文件名
        String fileName = "links_" + getCurrentTime() + "_readshare.json";
        // 调用新方法
        return exportToJson(context, links, fileName, null);
    }

    /**
     * 原始的导出到CSV方法（向后兼容）— 无 subject 关联信息
     */
    public static String exportToCsv(Context context, List<LinkItem> links) throws IOException {
        // 生成默认文件名
        String fileName = "links_" + getCurrentTime() + "_readshare.csv";
        // 调用新方法
        return exportToCsv(context, links, fileName, null);
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
     * @param linkIdToSubjectNames linkId → 它归属的 subject 名字列表;null 时相关列写空
     * @return 保存的文件 URI
     * @throws IOException 文件操作异常
     * @throws JSONException JSON 解析异常
     */
    public static Uri exportToPublicDirectory(Context context, List<LinkItem> links,
                                              boolean isJson,
                                              Map<Long, List<String>> linkIdToSubjectNames)
            throws IOException, JSONException {
        // 使用默认文件名（已包含扩展名）
        String defaultFileName = isJson
                ? "links_" + getCurrentTime() + "_readshare.json.txt"
                : "links_" + getCurrentTime() + "_readshare.csv";
        return exportToPublicDirectory(context, links, isJson, defaultFileName, linkIdToSubjectNames);
    }

    /**
     * 导出到公共 Documents 目录（支持自定义文件名）
     * 注意：文件名应该已经在调用前处理好了（添加扩展名等），这里直接使用
     * @param context Context
     * @param links 要导出的链接列表
     * @param isJson 是否为 JSON 格式（false 为 CSV）
     * @param fileName 已处理好的文件名（包含扩展名）
     * @param linkIdToSubjectNames linkId → 它归属的 subject 名字列表;null 时相关列写空
     * @return 保存的文件 URI
     * @throws IOException 文件操作异常
     * @throws JSONException JSON 解析异常
     */
    public static Uri exportToPublicDirectory(Context context, List<LinkItem> links,
                                              boolean isJson, String fileName,
                                              Map<Long, List<String>> linkIdToSubjectNames)
            throws IOException, JSONException {
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
                    writeDataToStream(outputStream, links, isJson, linkIdToSubjectNames);
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
            writeDataToStream(fos, links, isJson, linkIdToSubjectNames);
            fos.close();

            return Uri.fromFile(outputFile);
        }
    }

    /** 向后兼容 — 无 subject 信息 */
    public static Uri exportToPublicDirectory(Context context, List<LinkItem> links,
                                              boolean isJson) throws IOException, JSONException {
        return exportToPublicDirectory(context, links, isJson, (Map<Long, List<String>>) null);
    }

    /** 向后兼容 — 无 subject 信息 */
    public static Uri exportToPublicDirectory(Context context, List<LinkItem> links,
                                              boolean isJson, String fileName)
            throws IOException, JSONException {
        return exportToPublicDirectory(context, links, isJson, fileName, null);
    }

    /**
     * 将数据写入输出流
     * @param outputStream 输出流
     * @param links 链接列表
     * @param isJson 是否为 JSON 格式
     * @param linkIdToSubjectNames linkId → 它归属的 subject 名字列表;null 时相关列写空
     * @throws IOException 文件操作异常
     * @throws JSONException JSON 解析异常
     */
    private static void writeDataToStream(OutputStream outputStream, List<LinkItem> links,
                                         boolean isJson,
                                         Map<Long, List<String>> linkIdToSubjectNames)
            throws IOException, JSONException {
        OutputStreamWriter writer = new OutputStreamWriter(
                outputStream, StandardCharsets.UTF_8);

        if (isJson) {
            writer.write(buildJson(links, linkIdToSubjectNames));
        } else {
            writer.write(buildCsv(links, linkIdToSubjectNames));
        }

        writer.flush();
        writer.close();
    }

    /** 单一 CSV 构造入口 — exportToCsv / writeDataToStream 共用 */
    private static String buildCsv(List<LinkItem> links,
                                   Map<Long, List<String>> linkIdToSubjectNames) {
        StringBuilder csv = new StringBuilder();
        csv.append(CSV_HEADER).append('\n');
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        for (LinkItem link : links) {
            String title = escapeCSV(link.getTitle());
            String url = escapeCSV(link.getUrl());
            String date = sdf.format(new Date(link.getTimestamp()));
            String tags = escapeCSV(TextUtils.join(",", link.getTags()));
            String clickCount = String.valueOf(link.getClickCount());
            String summary = escapeCSV(link.getSummary());
            String pinned = link.isPinned() ? "1" : "0";
            String subjects = escapeCSV(joinSubjectNames(link.getId(), linkIdToSubjectNames));

            csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s\n",
                    title, url, date, tags, clickCount, summary, pinned, subjects));
        }
        return csv.toString();
    }

    /** 单一 JSON 构造入口 — exportToJson / writeDataToStream 共用 */
    private static String buildJson(List<LinkItem> links,
                                    Map<Long, List<String>> linkIdToSubjectNames) throws JSONException {
        JSONArray jsonArray = new JSONArray();
        for (LinkItem link : links) {
            try {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put(JSON_KEY_TITLE, link.getTitle());
                jsonObject.put(JSON_KEY_URL, link.getUrl());
                jsonObject.put(JSON_KEY_TAGS, new JSONArray(link.getTags()));
                jsonObject.put(JSON_KEY_IS_PINNED, link.isPinned());
                jsonObject.put(JSON_KEY_SUBJECTS, new JSONArray(
                        linkIdToSubjectNames == null ? Collections.emptyList()
                                : linkIdToSubjectNames.getOrDefault(link.getId(), Collections.emptyList())));
                jsonArray.put(jsonObject);
            } catch (JSONException e) {
                Log.e("ExportUtil", "Error creating JSON object", e);
            }
        }
        // 将 JSONObject 转义的反斜杠还原（\/ -> /），使 JSON 更易读
        return jsonArray.toString(4).replace("\\/", "/");
    }

    /** linkId → subject 名字(已 trim 前后空格),用 '|' 拼接;无归属返回 "" */
    private static String joinSubjectNames(long linkId, Map<Long, List<String>> linkIdToSubjectNames) {
        if (linkIdToSubjectNames == null) return "";
        List<String> names = linkIdToSubjectNames.get(linkId);
        if (names == null || names.isEmpty()) return "";
        List<String> trimmed = new ArrayList<>(names.size());
        for (String n : names) {
            if (n != null) {
                String t = n.trim();
                if (!t.isEmpty()) trimmed.add(t);
            }
        }
        return TextUtils.join("|", trimmed);
    }

    private static String escapeCSV(String value) {
        if (value == null) return "";
        value = value.replace("\"", "\"\""); // 转义双引号
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            value = "\"" + value + "\""; // 如果包含逗号或换行，用双引号包围
        }
        return value;
    }

    /**
     * 将应用的 SQLite 数据库文件 (links.db) 复制到 cacheDir/exports/，
     * 返回复制后的目标文件，供后续分享使用。
     *
     * @param context Android Context，用于解析源 db 路径与目标 cache 目录
     * @return 复制后的目标文件
     * @throws IOException 当源数据库文件不存在或读写失败时抛出
     */
    public static File exportDatabaseFile(Context context) throws IOException {
        if (context == null) {
            throw new IllegalArgumentException("context 不能为空");
        }
        File src = context.getDatabasePath("links.db");
        if (!src.exists()) {
            throw new IOException("数据库尚未初始化: " + src.getAbsolutePath());
        }

        File exportsDir = new File(context.getCacheDir(), "exports");
        if (!exportsDir.exists() && !exportsDir.mkdirs()) {
            throw new IOException("无法创建导出目录: " + exportsDir.getAbsolutePath());
        }

        String fileName = "links_" + getCurrentTime() + ".db";
        File dst = new File(exportsDir, fileName);
        copyFile(src, dst);
        return dst;
    }

    /**
     * 把源文件按字节复制到目标文件，使用 8KB 缓冲区。
     * 目标已存在时会被覆盖；源不存在时抛 IOException。
     *
     * 拆出来作为 public 是为了让单元测试能在没有 Context 的情况下直接覆盖。
     */
    public static void copyFile(File src, File dst) throws IOException {
        if (src == null || !src.exists()) {
            throw new IOException("源文件不存在: " + (src == null ? "null" : src.getAbsolutePath()));
        }
        if (dst == null) {
            throw new IllegalArgumentException("dst 不能为空");
        }
        byte[] buffer = new byte[8192];
        try (java.io.FileInputStream in = new java.io.FileInputStream(src);
             java.io.FileOutputStream out = new java.io.FileOutputStream(dst)) {
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
        }
    }
}