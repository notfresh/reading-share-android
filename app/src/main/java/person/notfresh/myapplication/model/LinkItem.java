package person.notfresh.myapplication.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class LinkItem {
    private long id;
    private String title;
    private String url;        // 只存储纯URL
    private String remark;     // 新增：存储其他内容
    private String sourceApp;
    private long timestamp;
    private String originalIntent;
    private String targetActivity;
    private List<String> tags;

    public LinkItem(String title, String url, String sourceApp, String originalIntent, String targetActivity) {
        this.title = title;
        this.url = extractUrl(url);  // 提取纯URL
        this.remark = extractRemark(url);  // 提取备注内容
        this.sourceApp = sourceApp;
        this.originalIntent = originalIntent;
        this.targetActivity = targetActivity;
        this.timestamp = System.currentTimeMillis();
        this.tags = new ArrayList<>();
    }

    public LinkItem(String title, String url, String sourceApp, String originalIntent, 
                   String targetActivity, long timestamp) {
        this(title, url, sourceApp, originalIntent, targetActivity);
        this.timestamp = timestamp;
    }

    // 提取URL的辅助方法
    private String extractUrl(String text) {
        int urlStart = text.indexOf("http");
        if (urlStart != -1) {
            String url = text.substring(urlStart);
            int spaceIndex = url.indexOf(" ");
            if (spaceIndex != -1) {
                url = url.substring(0, spaceIndex);
            }
            return url;
        }
        return text;
    }

    // 提取备注的辅助方法
    private String extractRemark(String text) {
        int urlStart = text.indexOf("http");
        if (urlStart != -1) {
            String beforeUrl = text.substring(0, urlStart).trim();
            String afterUrl = "";
            String url = text.substring(urlStart);
            int spaceIndex = url.indexOf(" ");
            if (spaceIndex != -1) {
                afterUrl = url.substring(spaceIndex + 1).trim();
            }
            
            if (!beforeUrl.isEmpty() && !afterUrl.isEmpty()) {
                return beforeUrl + " " + afterUrl;
            } else if (!beforeUrl.isEmpty()) {
                return beforeUrl;
            } else if (!afterUrl.isEmpty()) {
                return afterUrl;
            }
        }
        return "";
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

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

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