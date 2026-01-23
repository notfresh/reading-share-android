package person.notfresh.readingshare.util;

import android.content.Context;
import android.widget.Toast;

import androidx.fragment.app.FragmentManager;

import java.util.ArrayList;
import java.util.List;

import person.notfresh.readingshare.core.model.SubjectItem;
import person.notfresh.readingshare.db.SubjectDao;
import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.ui.subject.SelectSubjectDialog;

/**
 * 主题工具类
 * 提供主题相关的UI操作工具方法
 */
public class SubjectUtil {
    
    /**
     * 添加链接到主题（辅助方法，可被单个或多个链接调用）
     * 统一的添加到主题逻辑
     * 
     * @param context Context对象
     * @param fragmentManager FragmentManager用于显示对话框
     * @param items 要添加的链接列表
     */
    public static void addLinksToSubject(Context context, FragmentManager fragmentManager, List<LinkItem> items) {
        if (items == null || items.isEmpty()) {
            Toast.makeText(context, "请先选择要添加的链接", Toast.LENGTH_SHORT).show();
            return;
        }

        // 获取链接ID列表
        List<Long> linkIds = new ArrayList<>();
        for (LinkItem item : items) {
            linkIds.add(item.getId());
        }

        // 显示选择主题对话框
        SelectSubjectDialog dialog = SelectSubjectDialog.newInstance(linkIds);
        dialog.setOnSubjectSelectedListener((subjectId, selectedLinkIds) -> {
            // 批量创建 SubjectItem
            SubjectDao subjectDao = new SubjectDao(context);
            subjectDao.open();
            try {
                // 获取现有主题项，用于计算 orderIndex
                List<SubjectItem> existingItems = subjectDao.getSubjectItemsBySubjectId(subjectId);
                
                // 为每个链接创建 SubjectItem
                List<SubjectItem> newItems = new ArrayList<>();
                for (Long linkId : selectedLinkIds) {
                    SubjectItem item = new SubjectItem(subjectId);
                    item.setLinkId(linkId);
                    // 计算 orderIndex
                    int orderIndex = person.notfresh.readingshare.core.model.SubjectUtil.calculateOrderIndex(existingItems, -1);
                    item.setOrderIndex(orderIndex);
                    existingItems.add(item); // 添加到列表，用于下一个项的计算
                    newItems.add(item);
                }
                
                // 批量插入
                subjectDao.batchInsertSubjectItems(newItems);
                Toast.makeText(context, "已添加 " + newItems.size() + " 个链接到主题", Toast.LENGTH_SHORT).show();
            } finally {
                subjectDao.close();
            }
        });
        dialog.show(fragmentManager, "SelectSubjectDialog");
    }

    /**
     * 将单个链接直接添加到指定主题（不弹选择对话框）
     *
     * @param context Context对象
     * @param subjectId 主题ID
     * @param linkId 链接ID
     */
    public static void addLinkToSubjectById(Context context, long subjectId, long linkId) {
        SubjectDao subjectDao = new SubjectDao(context);
        subjectDao.open();
        try {
            List<SubjectItem> existingItems = subjectDao.getSubjectItemsBySubjectId(subjectId);
            SubjectItem item = new SubjectItem(subjectId);
            item.setLinkId(linkId);
            int orderIndex = person.notfresh.readingshare.core.model.SubjectUtil.calculateOrderIndex(existingItems, -1);
            item.setOrderIndex(orderIndex);
            List<SubjectItem> newItems = new ArrayList<>();
            newItems.add(item);
            subjectDao.batchInsertSubjectItems(newItems);
        } finally {
            subjectDao.close();
        }
    }
}
