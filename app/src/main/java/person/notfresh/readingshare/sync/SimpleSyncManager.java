package person.notfresh.readingshare.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.eventlog.EventLogClient;
import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.model.LinkJson;

/**
 * 轻量级同步管理器
 * 负责双向同步的核心逻辑：上传本地链接，下载服务器返回的链接
 */
public class SimpleSyncManager {
    private static final String TAG = "SimpleSyncManager";
    private static final String PREFS_NAME = "simple_sync";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_SECRET_KEY = "secret_key";

    private final Context context;
    private final LinkDao linkDao;

    public SimpleSyncManager(Context context) {
        this.context = context;
        this.linkDao = new LinkDao(context);
        this.linkDao.open();
    }

    public void close() {
        if (linkDao != null) {
            linkDao.close();
        }
    }

    /**
     * 保存同步配置
     */
    public void saveConfig(String serverUrl, String secretKey) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putString(KEY_SERVER_URL, serverUrl)
            .putString(KEY_SECRET_KEY, secretKey)
            .apply();
        Log.d(TAG, "保存同步配置: " + serverUrl);
    }

    /**
     * 获取服务器URL
     */
    public String getServerUrl() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SERVER_URL, "");
    }

    /**
     * 获取同步密钥
     */
    public String getSecretKey() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SECRET_KEY, "");
    }

    /**
     * 检查是否已配置
     */
    public boolean hasConfig() {
        String url = getServerUrl();
        String key = getSecretKey();
        return url != null && !url.isEmpty() && key != null && !key.isEmpty();
    }

    /**
     * Static, lightweight variants for callers that just need to read
     * the configured URL / secret without opening a LinkDao connection.
     * Instance methods above are unchanged for backward compatibility
     * with SettingFragment and other existing call sites.
     */
    public static String getServerUrl(Context context) {
        if (context == null) return "";
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SERVER_URL, "");
    }

    public static String getSecretKey(Context context) {
        if (context == null) return "";
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // 读时也 trim — 清理历史脏数据（旧 APK 没 trim 写入时存的带 \n secret）
        String v = prefs.getString(KEY_SECRET_KEY, "");
        return v == null ? "" : v.trim();
    }

    public static boolean hasConfig(Context context) {
        String url = getServerUrl(context);
        String key = getSecretKey(context);
        return url != null && !url.isEmpty() && key != null && !key.isEmpty();
    }

    /**
     * Static, independent setters. URL失焦写一个,secret失焦写一个,互不依赖 —
     * 比 {@link #saveConfig(String, String)} 联写更 SE(单一来源 / 各管各的)。
     * SettingFragment URL失焦 / secret失焦分别调各自 setter。
     */
    public static void saveServerUrl(Context context, String serverUrl) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SERVER_URL, serverUrl == null ? "" : serverUrl).apply();
        // 配置变更后让 EventLogClient 重新初始化(把 pusher/puller/syncConfig 接上)
        person.notfresh.readingshare.App.reinitIfConfigured(context);
    }

    public static void saveSecretKey(Context context, String secretKey) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // trim 写入 — 防粘贴时多带换行/空格导致 secret 末尾有 \n，
        // 进 HTTP Authorization 头会被 HttpURLConnection 抛 IllegalArgumentException
        String v = secretKey == null ? "" : secretKey.trim();
        prefs.edit().putString(KEY_SECRET_KEY, v).apply();
        // 配置变更后让 EventLogClient 重新初始化
        person.notfresh.readingshare.App.reinitIfConfigured(context);
    }

    /**
     * 计算链接的 hash
     * hash = SHA256(title + "::" + url)
     */
    public static String computeHash(String title, String url) {
        try {
            String input = title + "::" + url;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            Log.e(TAG, "计算Hash失败", e);
            return null;
        }
    }

    /**
     * 获取本地所有链接的 hash 集合
     */
    private Set<String> getLocalLinkHashes() {
        Set<String> hashes = new HashSet<>();
        List<LinkItem> allLinks = linkDao.getAllLinks();
        for (LinkItem link : allLinks) {
            String hash = computeHash(link.getTitle(), link.getUrl());
            if (hash != null) {
                hashes.add(hash);
            }
        }
        return hashes;
    }

    /**
     * 执行双向同步
     * 1. 上传本地所有链接到服务器
     * 2. 服务器返回本地缺少的链接
     * 3. 创建本地不存在的链接
     */
    public SyncResult sync() {
        if (!hasConfig()) {
            return SyncResult.failure("请先配置同步服务器地址和密钥");
        }

        try {
            String serverUrl = getServerUrl();
            String secretKey = getSecretKey();
            SyncApiClient client = new SyncApiClient(serverUrl, secretKey);

            // 1. 获取本地所有链接
            List<LinkItem> localLinks = linkDao.getAllLinks();
            Log.d(TAG, "本地链接数: " + localLinks.size());

            // 2. 构建请求 JSON
            JSONArray linksArray = new JSONArray();
            for (LinkItem link : localLinks) {
                JSONObject linkObj = new JSONObject();
                linkObj.put("title", link.getTitle());
                linkObj.put("url", link.getUrl());
                linkObj.put("hash", computeHash(link.getTitle(), link.getUrl()));
                linksArray.put(linkObj);
            }

            JSONObject requestJson = new JSONObject();
            requestJson.put("links", linksArray);

            // 3. 发送同步请求
            String responseJson = client.sync(requestJson.toString());
            if (responseJson == null) {
                return SyncResult.failure("同步请求失败，请检查网络和服务器配置");
            }

            // 4. 解析响应，获取服务器返回的链接
            JSONObject response = new JSONObject(responseJson);
            JSONArray serverLinks = response.getJSONArray("links");

            // 5. 获取本地已有的 hash
            Set<String> localHashes = getLocalLinkHashes();

            // 6. 创建本地不存在的链接
            int downloadedCount = 0;
            for (int i = 0; i < serverLinks.length(); i++) {
                JSONObject serverLink = serverLinks.getJSONObject(i);
                String hash = serverLink.getString("hash");

                if (!localHashes.contains(hash)) {
                    String title = serverLink.getString("title");
                    String url = serverLink.getString("url");

                    LinkItem newLink = new LinkItem(title, url, "sync", "", "");
                    linkDao.insertLink(newLink);
                    EventLogClient.get().create("links",
                            String.valueOf(newLink.getId()),
                            newLink.getTimestamp(),
                            LinkJson.toJsonString(newLink));
                    downloadedCount++;
                    Log.d(TAG, "创建新链接: " + title);
                }
            }

            Log.d(TAG, "同步完成: 上传 " + localLinks.size() + " 条, 下载 " + downloadedCount + " 条");
            return SyncResult.success(localLinks.size(), downloadedCount);

        } catch (Exception e) {
            Log.e(TAG, "同步失败", e);
            return SyncResult.failure("同步失败: " + e.getMessage());
        }
    }

    /**
     * 同步结果
     */
    public static class SyncResult {
        public final boolean success;
        public final String message;
        public final int uploadedCount;
        public final int downloadedCount;

        private SyncResult(boolean success, String message, int uploaded, int downloaded) {
            this.success = success;
            this.message = message;
            this.uploadedCount = uploaded;
            this.downloadedCount = downloaded;
        }

        public static SyncResult success(int uploaded, int downloaded) {
            return new SyncResult(true, "同步成功", uploaded, downloaded);
        }

        public static SyncResult failure(String message) {
            return new SyncResult(false, message, 0, 0);
        }
    }
}
