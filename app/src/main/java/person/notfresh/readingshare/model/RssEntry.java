package person.notfresh.readingshare.model;

public class RssEntry {
    private int id;
    private int sourceId;
    private String title;
    private String link;
    private long pubDate;

    public RssEntry(int sourceId, String title, String link, long pubDate) {
        this.sourceId = sourceId;
        this.title = title;
        this.link = link;
        this.pubDate = pubDate;
    }

    // Getters and setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public int getSourceId() { return sourceId; }
    public void setSourceId(int sourceId) { this.sourceId = sourceId; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
    
    public long getPubDate() { return pubDate; }
    public void setPubDate(long pubDate) { this.pubDate = pubDate; }

    @Override
    public String toString() {
        return title;
    }
} 