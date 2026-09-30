package person.notfresh.readingshare.links.strategy;

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

    @Override
    public boolean matches(String url) {
        if (url == null) return false;
        return url.contains(XHS_SHORT_LINK);
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
