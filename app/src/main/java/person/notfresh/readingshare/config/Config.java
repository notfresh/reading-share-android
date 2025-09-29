package person.notfresh.readingshare.config;

/**
 * 应用配置类
 * 集中管理应用中的各种配置信息
 */
public class Config {
    
    // API配置
    public static class API {
        // 度象AI摘要API
        public static final String DUXIANG_API_URL = "https://duxiang.ai/api/abstract";
        public static final String DUXIANG_API_KEY = "notfresh@duxiang.ai";
    }
    
    // 网络请求配置
    public static class Network {
        // 连接超时时间（毫秒）
        public static final int CONNECT_TIMEOUT = 3 * 1000;
        // 默认读取超时时间（毫秒）
        public static final int DEFAULT_READ_TIMEOUT = 10 * 1000;
        // 微信文章抓取超时时间（毫秒）
        public static final int WECHAT_CRAWL_TIMEOUT = 10000;
    }
    
    // 用户代理配置
    public static class UserAgent {
        public static final String CHROME_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    }
    
    // HTTP请求头配置
    public static class Headers {
        public static final String ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8";
        public static final String ACCEPT_LANGUAGE = "zh-CN,zh;q=0.9,en;q=0.8";
        public static final String ACCEPT_ENCODING = "gzip, deflate, br";
        public static final String CONNECTION = "keep-alive";
        public static final String CACHE_CONTROL = "max-age=0";
        public static final String CONTENT_TYPE_JSON = "application/json";
    }
    
    // 微信文章选择器配置
    public static class WeChatSelectors {
        public static final String TITLE_SELECTOR = "h1.rich_media_title";
        public static final String ACTIVITY_NAME_SELECTOR = "#activity-name";
    }
    
    // 其他配置
    public static class Misc {
        // 不限制响应大小
        public static final int MAX_BODY_SIZE = 0;
        // 允许重定向
        public static final boolean FOLLOW_REDIRECTS = true;
    }
}
