package person.notfresh.readingshare.model;

/**
 * 跳转白名单的动作枚举。
 * <p>与 WebViewActivity.handleUrlOverride 中既有的三档全局模式 (externalLinkMode)
 * 一一对应(对应值含义见常量说明),保证白名单命中与全局模式复用同一段处理逻辑,
 * 不引入新行为分支。</p>
 *
 * <p>对应关系:</p>
 * <ul>
 *   <li>{@link #ASK}   → 用户已经拍板 = 现 mode=0 弹窗确认</li>
 *   <li>{@link #OPEN}  → 现 mode=1 直接跳转</li>
 *   <li>{@link #BLOCK} → 现 mode=2 全部拦截</li>
 * </ul>
 */
public enum OverrideAction {
    ASK,
    OPEN,
    BLOCK
}
