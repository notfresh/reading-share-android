package person.notfresh.readingshare.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 主题项数据模型
 * 主题内的单个项目，可以包含图片、链接、备注
 * 内核类，与平台无关
 */
public class SubjectItem {
    private long id;
    private long subjectId;              // 所属主题ID
    private Long linkId;                 // 关联的LinkItem ID（可为null）
    private boolean isLinkDeleted;      // LinkItem删除标记（缓存）
    private String remark;               // 备注（多行文本，可为null）
    private long addTime;                // 添加时间
    private int orderIndex;              // 排序索引（间隔值：0, 10, 20, 30...）
    private List<String> images;         // 图片文件路径列表（最多10张）
    private boolean isArchived;          // 归档状态
    private long archivedAt;             // 归档时间

    // 常量
    public static final int MAX_IMAGES = 10;        // 最大图片数量
    public static final int ORDER_INTERVAL = 10;     // orderIndex间隔值

    public SubjectItem() {
        this.addTime = System.currentTimeMillis();
        this.images = new ArrayList<>();
        this.isLinkDeleted = false;
        this.orderIndex = 0;
        this.isArchived = false;
        this.archivedAt = 0;
    }

    public SubjectItem(long subjectId) {
        this();
        this.subjectId = subjectId;
    }

    /**
     * 验证SubjectItem的完整性
     * 必须至少包含：linkId、remark、images中的一项
     * @return true表示有效，false表示无效
     */
    public boolean isValid() {
        boolean hasLink = linkId != null && linkId > 0;
        boolean hasRemark = remark != null && !remark.trim().isEmpty();
        boolean hasImages = images != null && !images.isEmpty();
        return hasLink || hasRemark || hasImages;
    }

    /**
     * 验证图片数量是否超过限制
     * @return true表示有效，false表示超过限制
     */
    public boolean isImageCountValid() {
        return images == null || images.size() <= MAX_IMAGES;
    }

    /**
     * 添加图片路径
     * @param imagePath 图片文件路径
     * @return true表示添加成功，false表示已达到上限
     */
    public boolean addImage(String imagePath) {
        if (images == null) {
            images = new ArrayList<>();
        }
        if (images.size() >= MAX_IMAGES) {
            return false;
        }
        if (imagePath != null && !imagePath.trim().isEmpty()) {
            images.add(imagePath);
            return true;
        }
        return false;
    }

    /**
     * 移除图片路径
     * @param imagePath 要移除的图片路径
     * @return true表示移除成功
     */
    public boolean removeImage(String imagePath) {
        if (images == null) {
            return false;
        }
        return images.remove(imagePath);
    }

    /**
     * 移除指定索引的图片
     * @param index 图片索引
     * @return 被移除的图片路径，如果索引无效返回null
     */
    public String removeImageAt(int index) {
        if (images == null || index < 0 || index >= images.size()) {
            return null;
        }
        return images.remove(index);
    }

    /**
     * 清空所有图片
     */
    public void clearImages() {
        if (images != null) {
            images.clear();
        }
    }

    /**
     * 获取图片数量
     */
    public int getImageCount() {
        return images == null ? 0 : images.size();
    }

    // Getters and Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(long subjectId) {
        this.subjectId = subjectId;
    }

    public Long getLinkId() {
        return linkId;
    }

    public void setLinkId(Long linkId) {
        this.linkId = linkId;
    }

    public boolean isLinkDeleted() {
        return isLinkDeleted;
    }

    public void setLinkDeleted(boolean linkDeleted) {
        isLinkDeleted = linkDeleted;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public long getAddTime() {
        return addTime;
    }

    public void setAddTime(long addTime) {
        this.addTime = addTime;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public boolean isArchived() {
        return isArchived;
    }

    public void setArchived(boolean archived) {
        isArchived = archived;
    }

    public long getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(long archivedAt) {
        this.archivedAt = archivedAt;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
        // 确保不超过最大数量
        if (this.images != null && this.images.size() > MAX_IMAGES) {
            this.images = new ArrayList<>(this.images.subList(0, MAX_IMAGES));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SubjectItem that = (SubjectItem) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SubjectItem{" +
                "id=" + id +
                ", subjectId=" + subjectId +
                ", linkId=" + linkId +
                ", isLinkDeleted=" + isLinkDeleted +
                ", remark='" + remark + '\'' +
                ", addTime=" + addTime +
                ", orderIndex=" + orderIndex +
                ", isArchived=" + isArchived +
                ", archivedAt=" + archivedAt +
                ", imageCount=" + getImageCount() +
                '}';
    }
}

