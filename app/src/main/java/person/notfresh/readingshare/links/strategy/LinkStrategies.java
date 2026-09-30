package person.notfresh.readingshare.links.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 策略注册表（源码内配置文件）。
 *
 * <p>加新平台只需两步：</p>
 * <ol>
 *   <li>新增 XxxStrategy implements {@link LinkProcessStrategy}</li>
 *   <li>在本类 ALL 列表里加一行 {@code ALL.add(new XxxStrategy())}</li>
 * </ol>
 *
 * <p>主流程（MainActivity.fetchTitleFromUrl）不需要修改。</p>
 */
public final class LinkStrategies {

    private static final List<LinkProcessStrategy> ALL = new ArrayList<>();

    static {
        ALL.add(new XiaohongshuSkipStrategy());
    }

    private LinkStrategies() {
        // utility class
    }

    /** 返回第一个匹配的策略，没有命中则返回 Optional.empty() */
    public static Optional<LinkProcessStrategy> match(String url) {
        if (url == null) return Optional.empty();
        for (LinkProcessStrategy s : ALL) {
            if (s.matches(url)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }
}
