package person.notfresh.readingshare.util;

import android.util.Log;
import android.widget.EditText;

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
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

import person.notfresh.readingshare.config.Config;

public class CrawlUtil {

    public static String getWeixinArticleTitle(String url) throws IOException {
        // 使用jsoup连接并获取页面，添加更多请求头模拟真实浏览器
        Document doc = Jsoup.connect(url)
                .userAgent(Config.UserAgent.CHROME_USER_AGENT)
                .header("Accept", Config.Headers.ACCEPT)
                .header("Accept-Language", Config.Headers.ACCEPT_LANGUAGE)
                .header("Accept-Encoding", Config.Headers.ACCEPT_ENCODING)
                .header("Connection", Config.Headers.CONNECTION)
                .header("Cache-Control", Config.Headers.CACHE_CONTROL)
                .timeout(Config.Network.WECHAT_CRAWL_TIMEOUT)
                .maxBodySize(Config.Misc.MAX_BODY_SIZE)
                .followRedirects(Config.Misc.FOLLOW_REDIRECTS)
                .get();

        // 获取标题（尝试多种选择器）
        String title = doc.title();  // 先尝试获取页面标题
        if (title.isEmpty()) {
            // 如果页面标题为空，尝试获取文章标题
            title = doc.select(Config.WeChatSelectors.TITLE_SELECTOR).text();
        }
        if (title.isEmpty()) {
            // 如果还是空，尝试其他可能的选择器
            title = doc.select(Config.WeChatSelectors.ACTIVITY_NAME_SELECTOR).text();
        }

        // 如果所有方法都无法获取标题，返回空字符串
        return title;
    }

    public static String getArticleByUrl(String url) throws IOException {
        // 使用jsoup连接并获取页面，添加更多请求头模拟真实浏览器
        Document doc = Jsoup.connect(url)
                .userAgent(Config.UserAgent.CHROME_USER_AGENT)
                .header("Accept", Config.Headers.ACCEPT)
                .header("Accept-Language", Config.Headers.ACCEPT_LANGUAGE)
                .header("Accept-Encoding", Config.Headers.ACCEPT_ENCODING)
                .header("Connection", Config.Headers.CONNECTION)
                .header("Cache-Control", Config.Headers.CACHE_CONTROL)
                .timeout(Config.Network.WECHAT_CRAWL_TIMEOUT)
                .maxBodySize(Config.Misc.MAX_BODY_SIZE)
                .followRedirects(Config.Misc.FOLLOW_REDIRECTS)
                .get();
        // 提取文章内容
        StringBuilder contentBuilder = new StringBuilder();
        Log.d("WebViewActivityMenu", "@1.1");
        // 尝试提取掘金博客内容
        if (url.contains("juejin.cn")) {
            // 提取标题
            String title = doc.select("h2[data-id], h3[data-id], h4[data-id], h5[data-id], h6[data-id]").text();
            if (!title.isEmpty()) {
                contentBuilder.append("Title: ").append(title).append("\n\n");
            }

            // 提取文章主体内容
            org.jsoup.select.Elements contentElements = doc.select("div.article-viewer.markdown-body.result p, div.article-viewer.markdown-body.result h2, div.article-viewer.markdown-body.result h3, div.article-viewer.markdown-body.result h4, div.article-viewer.markdown-body.result h5, div.article-viewer.markdown-body.result h6, div.article-viewer.markdown-body.result li");
            for (org.jsoup.nodes.Element element : contentElements) {
                String text = element.text().trim();
                if (!text.isEmpty()) {
                    if (element.tagName().startsWith("h")) {
                        contentBuilder.append("\n").append(text).append("\n");
                    } else if (element.tagName().equals("li")) {
                        contentBuilder.append("- ").append(text).append("\n");
                    } else {
                        contentBuilder.append(text).append("\n");
                    }
                }
            }
            Log.d("WebViewActivityMenu", "@1.3");
        } else {
            // 对于其他网站，提取页面标题和主要内容
            String title = doc.title();
            if (!title.isEmpty()) {
                contentBuilder.append("Title: ").append(title).append("\n\n");
            }

            // 提取段落内容
            org.jsoup.select.Elements paragraphs = doc.select("p");
            for (org.jsoup.nodes.Element p : paragraphs) {
                String text = p.text().trim();
                if (!text.isEmpty() && text.length() > 10) {
                    contentBuilder.append(text).append("\n");
                }
            }
        }

        String webContent = contentBuilder.toString().trim();

        // 如果内容为空，返回页面标题
        if (webContent.isEmpty()) {
            webContent = doc.title();
        }

        return webContent;
    }

    public static String getUrlSummary(String url, int timeoutSeconds) throws IOException, JSONException, KeyManagementException, NoSuchAlgorithmException {
        // 构建API请求URL
        String apiUrl = Config.API.DUXIANG_API_URL;
        
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
        conn.setConnectTimeout(Config.Network.CONNECT_TIMEOUT);
        conn.setReadTimeout(timeoutSeconds * 1000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", Config.Headers.CONTENT_TYPE_JSON);
        
        // 构建请求JSON
        JSONObject requestJson = new JSONObject();
        requestJson.put("url", url);
        requestJson.put("key", Config.API.DUXIANG_API_KEY);
        
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


    public static String fetchTitleCommon(String url) throws IOException {
        String title = "";
        URL urlObj = new URL(url);
        URLConnection conn = urlObj.openConnection();
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)
        );

        StringBuilder html = new StringBuilder();
        String line;
        int linesRead = 0;
        while ((line = reader.readLine()) != null && linesRead < 100) {
            html.append(line);
            linesRead++;
        }
        reader.close();

        Pattern pattern = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html.toString());
        if (matcher.find()) {
            title = matcher.group(1).trim();
            // 如果标题超过20个字符，截取前20个字符并添加省略号
            if (title.length() > 20) {
                title = title.substring(0, 20) + "...";
            }
        }
        return title;
    }
}
