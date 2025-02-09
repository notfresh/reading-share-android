package person.notfresh.readingshare.util;

import java.io.BufferedReader;
import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;

public class CrawlUtil {

    public static String getWeixinArticleTitle(String url) throws IOException {
        // 使用jsoup连接并获取页面，添加更多请求头模拟真实浏览器
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Connection", "keep-alive")
                .header("Cache-Control", "max-age=0")
                .timeout(10000)  // 增加超时时间到10秒
                .maxBodySize(0)  // 不限制响应大小
                .followRedirects(true)  // 允许重定向
                .get();

        // 获取标题（尝试多种选择器）
        String title = doc.title();  // 先尝试获取页面标题
        if (title.isEmpty()) {
            // 如果页面标题为空，尝试获取文章标题
            title = doc.select("h1.rich_media_title").text();
        }
        if (title.isEmpty()) {
            // 如果还是空，尝试其他可能的选择器
            title = doc.select("#activity-name").text();
        }

        // 如果所有方法都无法获取标题，返回空字符串
        return title;
    }

    public static String getUrlSummary(String url, int timeoutSeconds) throws IOException, JSONException, KeyManagementException, NoSuchAlgorithmException {
        // 构建API请求URL
        String apiUrl = "https://duxiang.ai/api/abstract";
        
        // 创建连接
        URL apiUrlObj = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) apiUrlObj.openConnection();

        // 添加信任所有证书的配置
        if (conn instanceof javax.net.ssl.HttpsURLConnection) {
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
        }
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(3 * 1000);
        conn.setReadTimeout(timeoutSeconds * 1000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        
        // 构建请求JSON
        JSONObject requestJson = new JSONObject();
        requestJson.put("url", url);
        requestJson.put("key", "notfresh@duxiang.ai");
        
        // 写入请求体
        try (java.io.OutputStream os = conn.getOutputStream()) {
            byte[] input = requestJson.toString().getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        // 读取响应
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }

        // 解析JSON响应
        JSONObject jsonResponse = new JSONObject(response.toString());
        if (jsonResponse.getBoolean("success")) {
            JSONObject data = jsonResponse.getJSONObject("data");
            return data.getString("summary");
        }

        throw new IOException("Failed to get summary from API");
    }
}
