package person.notfresh.readingshare.model;

public class BookmarkItem {
    private long id;
    private long documentId;
    private int pageIndex;
    private String note;
    private long createdAt;

    public BookmarkItem() {}

    public BookmarkItem(long documentId, int pageIndex, String note) {
        this.documentId = documentId;
        this.pageIndex = pageIndex;
        this.note = note;
        this.createdAt = System.currentTimeMillis();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getDocumentId() { return documentId; }
    public void setDocumentId(long documentId) { this.documentId = documentId; }
    public int getPageIndex() { return pageIndex; }
    public void setPageIndex(int pageIndex) { this.pageIndex = pageIndex; }
    public int getPageNumber() { return pageIndex + 1; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
