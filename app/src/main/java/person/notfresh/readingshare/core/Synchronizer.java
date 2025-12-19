package person.notfresh.readingshare.core;

import android.util.Log;
import person.notfresh.readingshare.model.LinkItem;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * 同步器：将链接列表同步到指定的URL
 * 
 * 核心功能：
 * 1. 接受 List<LinkItem> 和 URL
 * 2. 将链接数据转换为JSON格式
 * 3. 通过POST请求发送到指定URL
 * 4. 返回同步结果
 */
public class Synchronizer {
    private static final String TAG = "Synchronizer";

    /**
     * 同步链接列表到指定URL
     * 
     * @param links 要同步的链接列表
     * @param targetUrl 目标URL
     * @return 同步结果
     */
    public SyncResult synchronize(List<LinkItem> links, String targetUrl) {
        return synchronize(links, targetUrl, null);
    }

    /**
     * 同步链接列表到指定URL（带额外数据）
     * 
     * @param links 要同步的链接列表
     * @param targetUrl 目标URL
     * @param extraData 额外的JSON数据（可选，会合并到请求体中）
     * @return 同步结果
     */
    public SyncResult synchronize(List<LinkItem> links, String targetUrl, JSONObject extraData) {
        if (links == null || links.isEmpty()) {
            return SyncResult.failure("链接列表为空");
        }
        
        if (targetUrl == null || targetUrl.isEmpty()) {
            return SyncResult.failure("目标URL为空");
        }

        try {
            // 构建JSON数据
            JSONObject jsonData = buildJsonData(links, extraData);
            Log.d(TAG, "构建的 JSON 数据: " + jsonData.toString());

            // 发送网络请求
            return sendRequest(targetUrl, jsonData);

        } catch (JSONException e) {
            Log.e(TAG, "JSON 构建失败", e);
            return SyncResult.failure("准备数据失败: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "同步失败", e);
            return SyncResult.failure("同步失败: " + e.getMessage());
        }
    }

    /**
     * 构建JSON数据
     */
    private JSONObject buildJsonData(List<LinkItem> links, JSONObject extraData) throws JSONException {
        JSONObject jsonData = new JSONObject();
        
        // 如果有额外数据，先合并进来
        if (extraData != null) {
            Iterator<String> keys = extraData.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                jsonData.put(key, extraData.get(key));
            }
        }
        
        // 构建链接数组
        JSONArray linksArray = new JSONArray();
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
        
        for (LinkItem link : links) {
            JSONObject linkObj = new JSONObject();
            linkObj.put("title", link.getTitle());
            linkObj.put("url", link.getUrl());
            linkObj.put("timestamp", isoFormat.format(new Date(link.getTimestamp())));
            linksArray.put(linkObj);
        }
        
        jsonData.put("links", linksArray);
        return jsonData;
    }

    /**
     * 发送HTTP请求
     */
    private SyncResult sendRequest(String targetUrl, JSONObject jsonData) {
        try {
            URL url = new URL(targetUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            
            // TODO 配置SSL（信任所有证书）
            //configureSSL(conn);
            
            // 设置请求方法和属性
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            
            Log.d(TAG, "开始发送数据到: " + targetUrl);
            
            // 发送请求体
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonData.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // 获取响应
            int responseCode = conn.getResponseCode();
            Log.d(TAG, "服务器响应码: " + responseCode);
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // 读取响应数据
                String responseData = readResponse(conn);
                return SyncResult.success(responseData, responseCode);
            } else {
                // 读取错误响应
                String errorResponse = readErrorResponse(conn);
                return SyncResult.failure("服务器返回错误: " + responseCode + 
                    (errorResponse != null ? " - " + errorResponse : ""), responseCode);
            }
            
        } catch (IOException e) {
            Log.e(TAG, "网络请求失败", e);
            return SyncResult.failure("网络请求失败: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "发送请求时出错", e);
            return SyncResult.failure("发送请求失败: " + e.getMessage());
        }
    }

    /**
     * 配置SSL（信任所有证书）
     * 注意：此方法存在安全风险，仅用于开发或内部网络
     */
    private void configureSSL(HttpURLConnection conn) {
        if (conn instanceof javax.net.ssl.HttpsURLConnection) {
            try {
                javax.net.ssl.HttpsURLConnection httpsConn = (javax.net.ssl.HttpsURLConnection) conn;
                javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[]{
                    new javax.net.ssl.X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }
                        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                        }
                        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                        }
                    }
                };

                javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("SSL");
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                httpsConn.setSSLSocketFactory(sc.getSocketFactory());
                httpsConn.setHostnameVerifier((hostname, session) -> true);
            } catch (Exception e) {
                Log.e(TAG, "配置SSL失败", e);
            }
        }
    }

    /**
     * 读取成功响应
     */
    private String readResponse(HttpURLConnection conn) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }

    /**
     * 读取错误响应
     */
    private String readErrorResponse(HttpURLConnection conn) {
        try {
            if (conn.getErrorStream() != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    return response.toString();
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "读取错误响应失败", e);
        }
        return null;
    }
}

