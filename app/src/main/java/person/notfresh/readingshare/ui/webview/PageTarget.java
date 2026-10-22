package person.notfresh.readingshare.ui.webview;

/**
 * 共享弹窗要操作的"当前页面"描述（目标2）。
 *
 * <p>加标签、改标题都需要同一组信息，抽成一个接口避免每个弹窗各定义一份 Target（单一来源）。
 * 两个宿主各自实现：Activity 用 currentUrl/pageTitleCache，阅读页用队列链接 id + WebView URL。</p>
 */
public interface PageTarget {

    /** 已知链接 id；null 表示需要按 url 解析。 */
    Long linkId();

    /** 当前页面 URL；linkId 为 null 时必须非空。 */
    String url();

    /** 重定向前的原始 URL；仅改标题的 URL 兜底用，可为空。 */
    String originalUrl();

    /** 页面标题；新建链接或预填输入框时用。 */
    String title();
}
