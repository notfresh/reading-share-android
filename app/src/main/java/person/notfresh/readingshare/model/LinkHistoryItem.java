package person.notfresh.readingshare.model;

public class LinkHistoryItem {
    private final long id;
    private final String title;
    private final String url;
    private final long visitedAt;

    public LinkHistoryItem(long id, String title, String url, long visitedAt) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.visitedAt = visitedAt;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public long getVisitedAt() {
        return visitedAt;
    }
}
