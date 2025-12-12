package person.notfresh.readingshare.model;

import java.util.Objects;

/**
 * 文档数据模型
 * 支持多种文档类型：PDF、Markdown、LaTeX、Text等
 */
public class DocumentItem {
    private long id;
    private String title;           // 文档标题
    private String filePath;        // 文件路径（应用私有目录）
    private DocumentType type;      // 文档类型
    private long timestamp;         // 创建时间
    private long fileSize;          // 文件大小（字节）
    private String remark;          // 备注
    private boolean isPinned;       // 是否置顶
    private int clickCount;         // 打开次数

    public DocumentItem() {
        this.timestamp = System.currentTimeMillis();
        this.clickCount = 0;
        this.isPinned = false;
    }

    public DocumentItem(String title, String filePath, DocumentType type) {
        this();
        this.title = title;
        this.filePath = filePath;
        this.type = type;
    }

    public DocumentItem(String title, String filePath, DocumentType type, long timestamp) {
        this(title, filePath, type);
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public DocumentType getType() {
        return type;
    }

    public void setType(DocumentType type) {
        this.type = type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public boolean isPinned() {
        return isPinned;
    }

    public void setPinned(boolean pinned) {
        isPinned = pinned;
    }

    public int getClickCount() {
        return clickCount;
    }

    public void setClickCount(int clickCount) {
        this.clickCount = clickCount;
    }

    public void incrementClickCount() {
        this.clickCount++;
    }

    /**
     * 格式化文件大小显示
     */
    public String getFormattedFileSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DocumentItem that = (DocumentItem) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

