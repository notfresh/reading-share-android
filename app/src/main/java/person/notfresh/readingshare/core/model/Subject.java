package person.notfresh.readingshare.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 主题数据模型
 * 用于聚合不同类型的内容（链接、图片、备注），促进灵感爆发，保证专注
 * 内核类，与平台无关
 */
public class Subject {
    private long id;
    private String title;                    // 主题标题
    private String describe;                 // 主题描述
    private long createTime;                 // 创建时间
    private List<SubjectItem> subItems;      // 主题项列表

    public Subject() {
        this.createTime = System.currentTimeMillis();
        this.subItems = new ArrayList<>();
    }

    public Subject(String title, String describe) {
        this();
        this.title = title;
        this.describe = describe;
    }

    public Subject(String title, String describe, long createTime) {
        this(title, describe);
        this.createTime = createTime;
    }

    /**
     * 添加主题项
     * @param item 主题项
     */
    public void addSubItem(SubjectItem item) {
        if (item != null) {
            if (subItems == null) {
                subItems = new ArrayList<>();
            }
            subItems.add(item);
        }
    }

    /**
     * 移除主题项
     * @param item 要移除的主题项
     * @return true表示移除成功
     */
    public boolean removeSubItem(SubjectItem item) {
        if (subItems == null || item == null) {
            return false;
        }
        return subItems.remove(item);
    }

    /**
     * 根据ID移除主题项
     * @param itemId 主题项ID
     * @return 被移除的主题项，如果不存在返回null
     */
    public SubjectItem removeSubItemById(long itemId) {
        if (subItems == null) {
            return null;
        }
        for (int i = 0; i < subItems.size(); i++) {
            SubjectItem item = subItems.get(i);
            if (item.getId() == itemId) {
                return subItems.remove(i);
            }
        }
        return null;
    }

    /**
     * 根据ID查找主题项
     * @param itemId 主题项ID
     * @return 找到的主题项，如果不存在返回null
     */
    public SubjectItem findSubItemById(long itemId) {
        if (subItems == null) {
            return null;
        }
        for (SubjectItem item : subItems) {
            if (item.getId() == itemId) {
                return item;
            }
        }
        return null;
    }

    /**
     * 获取主题项数量
     */
    public int getSubItemCount() {
        return subItems == null ? 0 : subItems.size();
    }

    /**
     * 清空所有主题项
     */
    public void clearSubItems() {
        if (subItems != null) {
            subItems.clear();
        }
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

    public String getDescribe() {
        return describe;
    }

    public void setDescribe(String describe) {
        this.describe = describe;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public List<SubjectItem> getSubItems() {
        return subItems;
    }

    public void setSubItems(List<SubjectItem> subItems) {
        this.subItems = subItems;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Subject subject = (Subject) o;
        return id == subject.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Subject{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", describe='" + describe + '\'' +
                ", createTime=" + createTime +
                ", subItemCount=" + getSubItemCount() +
                '}';
    }
}

