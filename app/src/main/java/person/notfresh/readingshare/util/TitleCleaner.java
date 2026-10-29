package person.notfresh.readingshare.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 标题清洗切入口 —— 链接创建前，按站点特化规则把标题里的广告话术清掉。
 *
 * <p><b>为什么有这个</b>：各平台「复制链接」分享出来的文案都带一段固定模板广告话术
 * （抖音的「复制打开抖音，看看……」）。用户只要描述，不要广告。</p>
 *
 * <p><b>怎么加新规则</b>：两步，跟 {@code links/strategy/LinkStrategies} 同一套思路。</p>
 * <ol>
 *   <li>在 {@code util/} 下写一个 {@code XxxTitleCleaner}，暴露
 *       {@code isXxxUrl(String url)} 和 {@code clean(String fullText)} 两个静态方法
 *       （{@code clean} 返回 null 表示「不适用」）</li>
 *   <li>在本类 {@link #RULES} 列表里加一行</li>
 * </ol>
 *
 * <p><b>调用方</b>：{@code MainActivity} 的两个入口（系统分享 / 剪切板检测），
 * 都在建 link 之前调 {@link #clean(String)}。</p>
 *
 * <p>规则全文见 {@code dev-docs/title-optimization-rules.md}。</p>
 */
public final class TitleCleaner {

    /**
     * URL 提取正则 —— 与 {@code MainActivity.handleClipboardText} 里用的保持一致。
     *
     * <p>单一来源：URL 怎么从分享全文里提取，只在这里定义一次。</p>
     */
    private static final Pattern URL_PATTERN =
            Pattern.compile("https?://[^\\s,，]+");

    /** 一条标题清洗规则：一个站点 + 一段改写逻辑。 */
    public interface Rule {
        /** 是否命中该站点。 */
        boolean matches(String url);

        /**
         * 清洗标题。
         *
         * @param fullText 分享全文（含 URL）
         * @return 清洗后的标题；返回 null 表示「本规则不适用」
         */
        String clean(String fullText);

        /** 规则名，用于日志/调试。 */
        String getName();
    }

    /** 规则注册表（源码内配置）。加新规则只需在这里加一行。 */
    private static final List<Rule> RULES = new ArrayList<>();

    static {
        // 优化规则一：抖音 —— 切掉「复制打开抖音，看看」这段广告前缀
        RULES.add(new Rule() {
            @Override
            public boolean matches(String url) {
                return DouyinTitleCleaner.isDouyinUrl(url);
            }

            @Override
            public String clean(String fullText) {
                return DouyinTitleCleaner.clean(fullText);
            }

            @Override
            public String getName() {
                return "douyin";
            }
        });
    }

    private TitleCleaner() {
        // utility class
    }

    /**
     * 从分享全文里提取第一个 http(s) URL。
     *
     * @param text 分享全文
     * @return URL；找不到返回 null
     */
    public static String extractUrl(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = URL_PATTERN.matcher(text);
        if (m.find()) {
            return m.group(0);
        }
        return null;
    }

    /**
     * 按站点规则清洗标题。
     *
     * <p>命中第一条规则即返回（当前规则都不需要链式叠加；将来若有需要，
     * 改成对所有命中规则依次清洗即可）。</p>
     *
     * @param fullText 分享全文（含 URL）—— 规则自己按 URL 边界切出标题范围
     * @return 清洗后的标题；未命中任何规则返回 null（调用方保留原标题）
     */
    public static String clean(String fullText) {
        if (fullText == null) {
            return null;
        }
        String url = extractUrl(fullText);
        if (url == null) {
            return null;
        }
        for (Rule rule : RULES) {
            if (rule.matches(url)) {
                String cleaned = rule.clean(fullText);
                if (cleaned != null) {
                    return cleaned;
                }
            }
        }
        return null;
    }
}
