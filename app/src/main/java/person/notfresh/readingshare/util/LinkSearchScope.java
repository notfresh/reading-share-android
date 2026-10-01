package person.notfresh.readingshare.util;

/**
 * 链接搜索的维度（用于 SubjectDetail 选链接等"在已有链接里挑一条"的场景）。
 *
 * <p>属于 UI/业务层概念，不依赖数据库；DAO 把 {@link #title} / {@link #url} / {@link #tag}
 * 翻译成对应的 LIKE 子句即可。
 */
public enum LinkSearchScope {
    ALL("title", "url", "tag"),
    TITLE("title"),
    URL("url"),
    TAG("tag");

    /** 暴露给调用方的字段名（仅用于日志/提示，不参与 SQL 拼接）。 */
    private final String[] fieldNames;

    LinkSearchScope(String... fieldNames) {
        this.fieldNames = fieldNames;
    }

    public String[] fieldNames() {
        return fieldNames;
    }
}