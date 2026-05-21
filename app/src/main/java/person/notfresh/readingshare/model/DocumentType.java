package person.notfresh.readingshare.model;

/**
 * 文档类型枚举
 * 支持多种文档格式：PDF、LaTeX、Markdown等
 */
public enum DocumentType {
    PDF("PDF", ".pdf", "application/pdf"),
    LATEX("LaTeX", ".tex", "text/plain"),
    MARKDOWN("Markdown", ".md", "text/markdown"),
    TEXT("文本", ".txt", "text/plain");

    private final String displayName;
    private final String extension;
    private final String mimeType;

    DocumentType(String displayName, String extension, String mimeType) {
        this.displayName = displayName;
        this.extension = extension;
        this.mimeType = mimeType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getExtension() {
        return extension;
    }

    public String getMimeType() {
        return mimeType;
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

