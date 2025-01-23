package person.notfresh.myapplication.util;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class BilibiliUrlConverter {

    public static String getRedirectedUrl(String shortUrl) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(shortUrl).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.connect();
        String redirectedUrl = connection.getHeaderField("Location");
        connection.disconnect();
        return redirectedUrl;
    }

    public static String convertToBilibiliScheme(String url) {
        if (url == null) {
            return null;
        }
        if (url.startsWith("https://www.bilibili.com/video/")) {
            return url.replace("https://www.bilibili.com/video/", "bilibili://video/");
        }
        return url;
    }
} 