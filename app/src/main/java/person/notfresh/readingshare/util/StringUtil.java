package person.notfresh.readingshare.util;

public class StringUtil {
    // TODO 理解这个函数
    // 从分享内容中提取标题
    public static String extractTitle(String text) {
        // 移除 URL
        String[] parts = text.split("\\s+");
        StringBuilder title = new StringBuilder();

        for (String part : parts) {
            if (!part.startsWith("http://") && !part.startsWith("https://")) {
                if (title.length() > 0) {
                    title.append(" ");
                }
                title.append(part);
            }
        }

        String result = title.toString().trim();
        return result.isEmpty() ? "分享的内容" : result;
    }
}
