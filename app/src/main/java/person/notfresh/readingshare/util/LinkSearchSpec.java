package person.notfresh.readingshare.util;

import java.util.Objects;

/**
 * 链接搜索的不可变参数。DAO 只关心这个，不直接接触 UI 字段名。
 *
 * <p>典型用法：
 * <pre>
 *   LinkSearchSpec spec = new LinkSearchSpec("foo", LinkSearchScope.ALL, 100);
 *   List&lt;LinkItem&gt; hits = linkDao.searchLinks(spec);
 * </pre>
 */
public final class LinkSearchSpec {

    private final String query;
    private final LinkSearchScope scope;
    private final int limit;

    public LinkSearchSpec(String query, LinkSearchScope scope, int limit) {
        this.query = query == null ? "" : query.trim();
        this.scope = Objects.requireNonNull(scope, "scope");
        this.limit = Math.max(0, limit);
    }

    public String query() { return query; }
    public LinkSearchScope scope() { return scope; }
    public int limit() { return limit; }

    public boolean isEmpty() { return query.isEmpty(); }

    /** 关键字两侧的 LIKE 通配符，仅 DAO 使用。 */
    public String likePattern() {
        return "%" + query + "%";
    }

    /** 解析 {@code "title:foo"} / {@code "tag:bar"} / {@code "url:baz"} 前缀；无前缀则返回 ALL。 */
    public static LinkSearchSpec parse(String raw, int defaultLimit) {
        if (raw == null) {
            return new LinkSearchSpec("", LinkSearchScope.ALL, defaultLimit);
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return new LinkSearchSpec("", LinkSearchScope.ALL, defaultLimit);
        }
        int colon = trimmed.indexOf(':');
        if (colon > 0 && colon < trimmed.length() - 1) {
            String prefix = trimmed.substring(0, colon).toLowerCase();
            String rest = trimmed.substring(colon + 1).trim();
            switch (prefix) {
                case "title": return new LinkSearchSpec(rest, LinkSearchScope.TITLE, defaultLimit);
                case "url":   return new LinkSearchSpec(rest, LinkSearchScope.URL,   defaultLimit);
                case "tag":   return new LinkSearchSpec(rest, LinkSearchScope.TAG,   defaultLimit);
                default: /* fall through, treat as plain */
            }
        }
        return new LinkSearchSpec(trimmed, LinkSearchScope.ALL, defaultLimit);
    }
}