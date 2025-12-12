package person.notfresh.readingshare.model;

/**
 * 文档类型枚举
 * 支持多种文档格式：PDF、LaTeX、Markdown等
 */
public enum DocumentType {
    PDF("PDF", ".pdf"),
    LATEX("LaTeX", ".tex"),
    MARKDOWN("Markdown", ".md"),
    TEXT("文本", ".txt");

    private final String displayName;
    private final String extension;

    DocumentType(String displayName, String extension) {
        this.displayName = displayName;
        this.extension = extension;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getExtension() {
        return extension;
    }

    /**
     * 根据文件路径判断文档类型
     */
    public static DocumentType fromFilePath(String filePath) {
        if (filePath == null) {
            return TEXT;
        }
        String lowerPath = filePath.toLowerCase();
        for (DocumentType type : values()) {
            if (lowerPath.endsWith(type.extension)) {
                return type;
            }
        }
        return TEXT; // 默认返回文本类型
    }

    /**
     * 根据类型名称获取枚举
     */
    public static DocumentType fromString(String typeName) {
        try {
            return valueOf(typeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return TEXT; // 默认返回文本类型
        }
    }
}

