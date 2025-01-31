package person.notfresh.readingshare.util;

import java.io.IOException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

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
}
