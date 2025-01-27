package person.notfresh.readingshare.model;

import java.util.Date;

public class RssEntry {
    private String title;
    private String link;
    private Date publishedDate;

    public RssEntry(String title, String link, long pubDate) {
        this.title = title;
        this.link = link;
        this.publishedDate = new Date(pubDate);
    }

    public RssEntry() {

    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public Date getPublishedDate() {
        return publishedDate;
    }

    public void setPublishedDate(Date publishedDate) {
        this.publishedDate = publishedDate;
    }

    @Override
    public String toString() {
        return title;
    }
} 