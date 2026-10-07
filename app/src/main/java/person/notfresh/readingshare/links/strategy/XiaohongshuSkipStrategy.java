package person.notfresh.readingshare.links.strategy;

import android.net.Uri;

/**
 * 小红书口令短链拦截策略。
 *
 * <p>小红书复制口令时，剪贴板先出现原始 xhslink.cn 链接，
 * 随后被覆盖为"找朋友上小红书"等官方推广文案。
 * 该策略命中 xhslink.cn 短链后跳过后台抓取，
 * 保留原始口令链接，避免被"官方标题"污染保存对话框。</p>
 */
public class XiaohongshuSkipStrategy implements LinkProcessStrategy {

    private static final String XHS_SHORT_LINK = "xhslink.cn";
    private static final String XHS_MAIN_DOMAIN = "xiaohongshu.com";

    /**
     * 是否小红书域(包括短链 xhslink 系列 + 主域 xiaohongshu.com 及子域)。
     * 单一来源,被 {@link XiaohongshuSkipStrategy#matches(String)} 和
     * {@code WebViewActivity#isXshlinkUrl(String)} 共同引用。
     */
    public static boolean isXiaohongshuUrl(String url) {
        if (url == null) return false;
        String trimmed = url.trim().toLowerCase(java.util.Locale.ROOT);
        if (trimmed.isEmpty()) return false;
        // 短链系列:用 contains 即可(短链无歧义)
        if (trimmed.contains(XHS_SHORT_LINK)) return true;
        // 主域:用 host 判断,避免误伤含 "xiaohongshu" 字样的其他站
        String host = Uri.parse(trimmed).getHost();
        if (host == null) return false;
        return host.equals(XHS_MAIN_DOMAIN) || host.endsWith("." + XHS_MAIN_DOMAIN);
    }

    @Override
    public boolean matches(String url) {
        return isXiaohongshuUrl(url);
    }

    @Override
    public Action action() {
        return Action.SKIP;
    }

    @Override
    public String getName() {
        return "XiaohongshuSkip";
    }
}
