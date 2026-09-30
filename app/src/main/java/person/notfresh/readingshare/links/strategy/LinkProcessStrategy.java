package person.notfresh.readingshare.links.strategy;

/**
 * 链接处理策略接口。
 * 用于在后台抓取真实标题/URL 的入口处拦截特定平台的链接处理逻辑。
 * 加新平台只需新增实现类并在 {@link LinkStrategies} 注册即可，主流程不动。
 */
public interface LinkProcessStrategy {

    enum Action {
        /** 命中后跳过抓取，保留原始链接 */
        SKIP,
        /** 命中后按默认流程抓取（与未命中一致） */
        FETCH
    }

    /** 是否命中该策略 */
    boolean matches(String url);

    /** 命中后采取的动作 */
    Action action();

    /** 策略名，用于日志/调试 */
    String getName();
}
