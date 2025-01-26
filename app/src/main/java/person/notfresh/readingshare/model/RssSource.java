package person.notfresh.readingshare.model;

public class RssSource {
    private int id;
    private String url;
    private String name;
    private long lastUpdate;

    public RssSource(String url, String name) {
        this.url = url;
        this.name = name;
        this.lastUpdate = System.currentTimeMillis();
    }

    // Getters and setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getUrl() { return url; }
    public String getName() { return name; }
    public long getLastUpdate() { return lastUpdate; }
    public void setLastUpdate(long lastUpdate) { this.lastUpdate = lastUpdate; }

    @Override
    public String toString() {
        return name; // 用于在 Spinner 中显示
    }
} 