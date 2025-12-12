package person.notfresh.readingshare.ui.document;

/**
 * PDF目录项（书签/大纲）
 */
public class TocOutlineItem {
    private String title;      // 目录标题
    private int pageIndex;     // 页码（0-based）
    private int level;         // 层级（0为顶级，1为二级，以此类推）

    public TocOutlineItem(String title, int pageIndex) {
        this(title, pageIndex, 0);
    }

    public TocOutlineItem(String title, int pageIndex, int level) {
        this.title = title;
        this.pageIndex = pageIndex;
        this.level = level;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public void setPageIndex(int pageIndex) {
        this.pageIndex = pageIndex;
    }

    public int getPageNumber() {
        return pageIndex + 1; // 转换为1-based页码
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }
}

