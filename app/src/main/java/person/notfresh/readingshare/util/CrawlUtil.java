package person.notfresh.readingshare.util;

import android.util.Log;
import android.widget.EditText;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;

import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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

    /**
     * 快速获取网站的 favicon URL
     * 使用流式读取，只读取 HTML 的 head 部分，找到 </head> 即停止
     * @param url 网站 URL
     * @return favicon URL，如果未找到则返回 null
     */
    public static String getFaviconUrl(String url) throws IOException {
        try {
            URL urlObj = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
            
            // 快速超时设置（参考 fetchTitleCommon）
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", Config.UserAgent.CHROME_USER_AGENT);
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            
            // 流式读取 HTML，只读取到 </head> 为止
            StringBuilder html = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                int linesRead = 0;
                final int MAX_LINES = 500;  // 最多读取 500 行
                final int MAX_SIZE = 50 * 1024;  // 最多 50KB
                
                while ((line = reader.readLine()) != null && linesRead < MAX_LINES) {
                    html.append(line).append("\n");
                    linesRead++;
                    
                    // 检查是否找到 </head> 标签，找到就立即停止
                    String htmlStr = html.toString().toLowerCase();
                    if (htmlStr.contains("</head>")) {
                        Log.d("CrawlUtil", "Found </head> tag, stopping at line " + linesRead);
                        break;
                    }
                    
                    // 防止读取过多内容
                    if (html.length() > MAX_SIZE) {
                        Log.d("CrawlUtil", "Reached max size limit, stopping");
                        break;
                    }
                }
            }
            
            // 使用 Jsoup 解析 HTML 片段（只解析 head 部分）
            Document doc = Jsoup.parse(html.toString());
            
            // 优先查找标准 favicon 链接
            String faviconUrl = null;
            
            // 1. 查找 <link rel="icon">
            Elements iconLinks = doc.select("link[rel=icon]");
            if (!iconLinks.isEmpty()) {
                faviconUrl = iconLinks.first().attr("href");
                Log.d("CrawlUtil", "Found favicon via rel=icon: " + faviconUrl);
            }
            
            // 2. 如果没找到，查找 <link rel="shortcut icon">
            if (faviconUrl == null || faviconUrl.isEmpty()) {
                Elements shortcutIconLinks = doc.select("link[rel=shortcut icon]");
                if (!shortcutIconLinks.isEmpty()) {
                    faviconUrl = shortcutIconLinks.first().attr("href");
                    Log.d("CrawlUtil", "Found favicon via rel=shortcut icon: " + faviconUrl);
                }
            }
            
            // 3. 如果还没找到，尝试 apple-touch-icon（通常也可以作为 favicon）
            if (faviconUrl == null || faviconUrl.isEmpty()) {
                Elements appleIconLinks = doc.select("link[rel=apple-touch-icon]");
                if (!appleIconLinks.isEmpty()) {
                    faviconUrl = appleIconLinks.first().attr("href");
                    Log.d("CrawlUtil", "Found favicon via rel=apple-touch-icon: " + faviconUrl);
                }
            }
            
            // 如果从 HTML 中找到了 favicon URL，需要转换为绝对 URL
            if (faviconUrl != null && !faviconUrl.isEmpty()) {
                faviconUrl = resolveUrl(url, faviconUrl);
                Log.d("CrawlUtil", "Resolved favicon URL: " + faviconUrl);
                return faviconUrl;
            }
            
            // 如果 HTML 中没找到，尝试标准路径 /favicon.ico
            String baseUrl = urlObj.getProtocol() + "://" + urlObj.getHost();
            if (urlObj.getPort() != -1 && urlObj.getPort() != 80 && urlObj.getPort() != 443) {
                baseUrl += ":" + urlObj.getPort();
            }
            String defaultFavicon = baseUrl + "/favicon.ico";
            Log.d("CrawlUtil", "No favicon found in HTML, trying default: " + defaultFavicon);
            
            // 验证默认路径是否存在（可选，这里直接返回，让下载时验证）
            return defaultFavicon;
            
        } catch (Exception e) {
            Log.e("CrawlUtil", "Error getting favicon URL: " + e.getMessage(), e);
            // 如果出错，返回默认 favicon.ico 路径
            try {
                URL urlObj = new URL(url);
                String baseUrl = urlObj.getProtocol() + "://" + urlObj.getHost();
                if (urlObj.getPort() != -1 && urlObj.getPort() != 80 && urlObj.getPort() != 443) {
                    baseUrl += ":" + urlObj.getPort();
                }
                return baseUrl + "/favicon.ico";
            } catch (Exception ex) {
                Log.e("CrawlUtil", "Error constructing default favicon URL", ex);
                return null;
            }
        }
    }
    
    /**
     * 将相对 URL 转换为绝对 URL
     * @param baseUrl 基础 URL
     * @param relativeUrl 相对 URL
     * @return 绝对 URL
     */
    private static String resolveUrl(String baseUrl, String relativeUrl) {
        if (relativeUrl == null || relativeUrl.isEmpty()) {
            return null;
        }
        
        try {
            // 如果已经是绝对 URL，直接返回
            if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
                return relativeUrl;
            }
            
            URL base = new URL(baseUrl);
            
            // 处理协议相对 URL：//example.com/favicon.ico
            if (relativeUrl.startsWith("//")) {
                return base.getProtocol() + ":" + relativeUrl;
            }
            
            // 处理绝对路径：/favicon.ico
            if (relativeUrl.startsWith("/")) {
                return base.getProtocol() + "://" + base.getHost() + 
                       (base.getPort() != -1 && base.getPort() != 80 && base.getPort() != 443 ? ":" + base.getPort() : "") + 
                       relativeUrl;
            }
            
            // 处理相对路径：favicon.ico 或 ../favicon.ico
            URL resolved = new URL(base, relativeUrl);
            return resolved.toString();
            
        } catch (Exception e) {
            Log.e("CrawlUtil", "Error resolving URL: " + e.getMessage(), e);
            // 如果解析失败，尝试简单拼接
            if (relativeUrl.startsWith("/")) {
                try {
                    URL base = new URL(baseUrl);
                    return base.getProtocol() + "://" + base.getHost() + relativeUrl;
                } catch (Exception ex) {
                    return null;
                }
            }
            return null;
        }
    }
    
    /**
     * 下载 favicon 图片并转换为 Bitmap
     * @param faviconUrl favicon 图片 URL
     * @return Bitmap 对象，如果下载失败返回 null
     */
    public static Bitmap downloadFavicon(String faviconUrl) {
        if (faviconUrl == null || faviconUrl.isEmpty()) {
            Log.w("CrawlUtil", "Favicon URL is null or empty");
            return null;
        }
        
        try {
            URL url = new URL(faviconUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            
            // 设置快速超时
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", Config.UserAgent.CHROME_USER_AGENT);
            
            // 下载图片
            try (InputStream inputStream = conn.getInputStream()) {
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                
                if (bitmap != null) {
                    Log.d("CrawlUtil", "Successfully downloaded favicon, size: " + 
                          bitmap.getWidth() + "x" + bitmap.getHeight());
                    
                    // 如果图片太大，缩放一下（快捷方式图标通常不需要太大）
                    if (bitmap.getWidth() > 256 || bitmap.getHeight() > 256) {
                        int newSize = Math.min(256, Math.max(bitmap.getWidth(), bitmap.getHeight()));
                        float scale = (float) newSize / Math.max(bitmap.getWidth(), bitmap.getHeight());
                        int newWidth = (int) (bitmap.getWidth() * scale);
                        int newHeight = (int) (bitmap.getHeight() * scale);
                        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                        bitmap.recycle(); // 释放原图内存
                        bitmap = scaledBitmap;
                        Log.d("CrawlUtil", "Scaled favicon to: " + newWidth + "x" + newHeight);
                    }
                    
                    return bitmap;
                } else {
                    Log.w("CrawlUtil", "Failed to decode favicon bitmap");
                    return null;
                }
            }
            
        } catch (Exception e) {
            Log.e("CrawlUtil", "Error downloading favicon from " + faviconUrl + ": " + e.getMessage(), e);
            return null;
        }
    }
}
