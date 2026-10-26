package person.notfresh.readingshare.util;

import java.util.Locale;

/**
 * 抖音分享文案的标题清洗规则（标题优化规则一）。
 *
 * <p>抖音「复制链接」分享出来的文案，前面总是一段固定模板广告话术：</p>
 * <pre>
 * 1.00 复制打开抖音，看看【南巷旧人的作品】# 格斗魅力 # 暴力美学 # 硬帅天花板 https://v.douyin.com/lq35T16GunU/ :5pm xsE:/ 03/08 s@E.ho
 * └──────────── 从开头切到「看看」之后 ────────────┘└──────────── 剩下的原样保留 ────────────┘
 * </pre>
 *
 * <p><b>规则</b>：从字符串<b>开头</b>一路切到「看看」两个字<b>之后</b>为止，
 * 剩下的部分原样保留（直到 URL 开始为止）。</p>
 *
 * <p><b>刻意不做的事</b>（用户拍板的设计取舍）：</p>
 * <ul>
 *   <li>不拆 {@code 【】} —— 作者名是有价值的内容，不是广告，保留</li>
 *   <li>不拆 {@code #} 话题标签 —— 保留</li>
 *   <li>不砍尾部 {@code ...} —— 抖音对长标题的截断标记，保留</li>
 *   <li>不写死「复制打开抖音」这个具体话术 —— 锚点只用「看看」两个字，
 *       抖音改文案模板时（如换成「点击打开抖音，看看」）规则依然生效</li>
 * </ul>
 *
 * <p>规则全文见 {@code dev-docs/title-optimization-rules.md}。</p>
 *
 * @see TitleCleaner
 */
public final class DouyinTitleCleaner {

    /** 切分锚点：抖音模板里「看看」两个字。切到它之后为止。 */
    private static final String ANCHOR = "看看";

    /** 抖音主域名（用于 host 判定）。 */
    private static final String DOMAIN = "douyin.com";

    /** 抖音短链域名。 */
    private static final String SHORT_DOMAIN = "v.douyin.com";

    private DouyinTitleCleaner() {
        // utility class
    }

    /**
     * 是否是抖音链接。
     *
     * <p>用 host 判定而不是字符串 contains，避免误伤「某 douyin.com.evil.com」这类域名。</p>
     *
     * <p>host 用纯字符串解析（不用 {@code android.net.Uri}）—— 纯 Java 实现才能跑
     * JUnit 单测（本项目 {@code android.jar} 里的 Uri 是 stub，会抛「not mocked」）。</p>
     *
     * @param url 待判定的 URL
     * @return true = 抖音链接
     */
    public static boolean isDouyinUrl(String url) {
        String host = hostOf(url);
        if (host == null) {
            return false;
        }
        return host.equals(DOMAIN)
                || host.endsWith("." + DOMAIN)
                || host.equals(SHORT_DOMAIN)
                || host.endsWith("." + SHORT_DOMAIN);
    }

    /**
     * 从 URL 里取 host（纯字符串解析）。
     *
     * @return host（小写）；取不到返回 null
     */
    private static String hostOf(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        int schemeEnd = url.indexOf("://");
        if (schemeEnd < 0) {
            return null;
        }
        int hostStart = schemeEnd + 3;
        int hostEnd = url.length();
        for (int i = hostStart; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '/' || c == '?' || c == '#' || c == ':' || c == ' ') {
                hostEnd = i;
                break;
            }
        }
        if (hostEnd <= hostStart) {
            return null;
        }
        return url.substring(hostStart, hostEnd).toLowerCase(Locale.US);
    }

    /**
     * 清洗抖音分享文案的标题：切掉开头到「看看」之后的广告前缀。
     *
     * @param fullText 分享全文（含 URL）
     * @return 清洗后的标题；未命中返回 null（表示「本规则不适用」）
     */
    public static String clean(String fullText) {
        if (fullText == null) {
            return null;
        }
        int anchorIdx = fullText.indexOf(ANCHOR);
        if (anchorIdx < 0) {
            // 没有锚点（如「长按复制此条消息，打开抖音搜索，观看视频！」那种模板）→ 不命中
            return null;
        }
        int cut = anchorIdx + ANCHOR.length();

        // 标题范围：切分点之后，到 URL 开始之前。URL 之后是抖音的尾部乱码
        // （如 ":5pm xsE:/ 03/08 s@E.ho"），不要。
        int urlStart = indexOfUrl(fullText, cut);
        String head = urlStart < 0
                ? fullText.substring(cut)
                : fullText.substring(cut, urlStart);
        return head.trim();
    }

    /**
     * 从 {@code from} 开始找第一个 http(s) URL 的起始位置。
     *
     * @return 找不到返回 -1
     */
    private static int indexOfUrl(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            if (text.startsWith("http://", i) || text.startsWith("https://", i)) {
                return i;
            }
        }
        return -1;
    }
}
