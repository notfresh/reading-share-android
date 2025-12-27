package person.notfresh.readingshare.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 主题内核服务类
 * 提供主题和主题项的业务工具类，与平台无关 工具类，不需要实例化，直接使用静态方法即可。
 * 包括：排序、验证、orderIndex计算等
 */
public class SubjectUtil {

    // 常量
    public static final int ORDER_INTERVAL = 10;        // orderIndex间隔值
    public static final int ORDER_START = 0;            // orderIndex起始值

    /**
     * 计算新主题项的orderIndex
     * 在指定位置插入新项时使用
     * 
     * @param existingItems 现有的主题项列表（已按orderIndex排序）
     * @param insertPosition 插入位置（0表示最前面，-1表示最后面）
     * @return 计算出的orderIndex值
     */
    public static int calculateOrderIndex(List<SubjectItem> existingItems, int insertPosition) {
        if (existingItems == null || existingItems.isEmpty()) {
            // 第一个项，从0开始
            return ORDER_START;
        }

        // 如果插入位置为-1，表示插入到最后
        if (insertPosition == -1 || insertPosition >= existingItems.size()) {
            // 找到最大的orderIndex，然后加上间隔
            int maxOrderIndex = ORDER_START;
            for (SubjectItem item : existingItems) {
                if (item.getOrderIndex() > maxOrderIndex) {
                    maxOrderIndex = item.getOrderIndex();
                }
            }
            return maxOrderIndex + ORDER_INTERVAL;
        }

        // 插入到指定位置
        if (insertPosition == 0) {
            // 插入到最前面
            int firstOrderIndex = existingItems.get(0).getOrderIndex();
            if (firstOrderIndex >= ORDER_INTERVAL) {
                // 如果第一个项的orderIndex >= 间隔值，可以直接减去间隔值
                return firstOrderIndex - ORDER_INTERVAL;
            } else {
                // 否则需要调整所有项
                return ORDER_START;
            }
        }

        // 插入到中间位置
        SubjectItem prevItem = existingItems.get(insertPosition - 1);
        SubjectItem nextItem = existingItems.get(insertPosition);
        
        int prevOrder = prevItem.getOrderIndex();
        int nextOrder = nextItem.getOrderIndex();
        
        // 计算中间值
        int middleOrder = (prevOrder + nextOrder) / 2;
        
        // 如果中间值等于前一个或后一个，说明间隔已满，需要调整
        if (middleOrder == prevOrder || middleOrder == nextOrder) {
            // 间隔已满，需要调整后续项
            return prevOrder + 1; // 临时值，实际使用时需要先调整后续项
        }
        
        return middleOrder;
    }

    /**
     * 调整orderIndex间隔
     * 当某个区间的间隔耗尽时，将所有orderIndex >= threshold的项统一加10
     * 
     * @param items 需要调整的主题项列表
     * @param threshold 阈值，所有orderIndex >= threshold的项都会被调整
     */
    public static void adjustOrderIndexInterval(List<SubjectItem> items, int threshold) {
        if (items == null || items.isEmpty()) {
            return;
        }

        for (SubjectItem item : items) {
            if (item.getOrderIndex() >= threshold) {
                item.setOrderIndex(item.getOrderIndex() + ORDER_INTERVAL);
            }
        }
    }

    /**
     * 检查并调整orderIndex间隔
     * 如果指定位置的前后项之间间隔不足，则调整后续项
     * 
     * @param items 主题项列表（已按orderIndex排序）
     * @param insertPosition 插入位置
     * @return true表示需要调整，false表示不需要
     */
    public static boolean checkAndAdjustInterval(List<SubjectItem> items, int insertPosition) {
        if (items == null || items.size() < 2) {
            return false;
        }

        if (insertPosition <= 0 || insertPosition >= items.size()) {
            return false;
        }

        SubjectItem prevItem = items.get(insertPosition - 1);
        SubjectItem nextItem = items.get(insertPosition);
        
        int prevOrder = prevItem.getOrderIndex();
        int nextOrder = nextItem.getOrderIndex();
        
        // 如果间隔小于等于1，说明间隔已满
        if (nextOrder - prevOrder <= 1) {
            // 调整后续项
            adjustOrderIndexInterval(items, nextOrder);
            return true;
        }
        
        return false;
    }

    /**
     * 对主题项列表按orderIndex排序
     * 
     * @param items 主题项列表
     */
    public static void sortByOrderIndex(List<SubjectItem> items) {
        if (items == null || items.size() <= 1) {
            return;
        }
        Collections.sort(items, new Comparator<SubjectItem>() {
            @Override
            public int compare(SubjectItem o1, SubjectItem o2) {
                return Integer.compare(o1.getOrderIndex(), o2.getOrderIndex());
            }
        });
    }

    /**
     * 验证主题项是否有效
     * 必须至少包含：linkId、remark、images中的一项
     * 
     * @param item 主题项
     * @return true表示有效，false表示无效
     */
    public static boolean validateSubjectItem(SubjectItem item) {
        if (item == null) {
            return false;
        }
        return item.isValid();
    }

    /**
     * 验证图片数量是否有效
     * 
     * @param item 主题项
     * @return true表示有效，false表示超过限制
     */
    public static boolean validateImageCount(SubjectItem item) {
        if (item == null) {
            return false;
        }
        return item.isImageCountValid();
    }

    /**
     * 批量验证主题项
     * 
     * @param items 主题项列表
     * @return 无效的主题项列表
     */
    public static List<SubjectItem> validateSubjectItems(List<SubjectItem> items) {
        List<SubjectItem> invalidItems = new ArrayList<>();
        if (items == null) {
            return invalidItems;
        }
        
        for (SubjectItem item : items) {
            if (!validateSubjectItem(item)) {
                invalidItems.add(item);
            }
        }
        
        return invalidItems;
    }

    /**
     * 计算拖拽后的orderIndex
     * 当用户拖拽主题项到新位置时使用
     * 
     * @param items 所有主题项列表（已按orderIndex排序）
     * @param draggedItem 被拖拽的项
     * @param newPosition 新位置
     * @return 新的orderIndex值
     */
    public static int calculateDragOrderIndex(List<SubjectItem> items, SubjectItem draggedItem, int newPosition) {
        if (items == null || draggedItem == null) {
            return ORDER_START;
        }

        // 创建临时列表（排除被拖拽的项）
        List<SubjectItem> tempItems = new ArrayList<>();
        for (SubjectItem item : items) {
            if (item.getId() != draggedItem.getId()) {
                tempItems.add(item);
            }
        }

        // 如果列表为空，返回起始值
        if (tempItems.isEmpty()) {
            return ORDER_START;
        }

        // 确保列表已排序
        sortByOrderIndex(tempItems);

        // 计算新位置的orderIndex
        return calculateOrderIndex(tempItems, newPosition);
    }

    /**
     * 重新计算所有主题项的orderIndex
     * 使用连续的间隔值：0, 10, 20, 30...
     * 
     * @param items 主题项列表（会按当前orderIndex排序后重新分配）
     */
    public static void recalculateAllOrderIndex(List<SubjectItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        // 先排序
        sortByOrderIndex(items);

        // 重新分配orderIndex
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setOrderIndex(i * ORDER_INTERVAL);
        }
    }
}

