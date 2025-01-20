package person.notfresh.myapplication.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class LinkItem {
    private long id;
    private String title;
    private String url;
    private String sourceApp;
    private long timestamp;
    private String originalIntent;
    private String targetActivity;
    private List<String> tags;

    public LinkItem(String title, String url, String sourceApp, String originalIntent, String targetActivity) {
        this.title = title;
        this.url = url;
        this.sourceApp = sourceApp;
        this.originalIntent = originalIntent;
        this.targetActivity = targetActivity;
        this.timestamp = System.currentTimeMillis();
        this.tags = new ArrayList<>();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public String getSourceApp() { return sourceApp; }
    public long getTimestamp() { return timestamp; }
    public String getOriginalIntent() { return originalIntent; }
    public String getTargetActivity() { return targetActivity; }
    public List<String> getTags() { return tags; }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setTags(List<String> tags) { this.tags = tags; }
    public void addTag(String tag) { 
        if (!tags.contains(tag)) {
            tags.add(tag); 
        }
    }
    public void removeTag(String tag) { tags.remove(tag); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LinkItem linkItem = (LinkItem) o;
        return id == linkItem.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
} 