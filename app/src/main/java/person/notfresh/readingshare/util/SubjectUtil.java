package person.notfresh.readingshare.util;

import android.content.Context;
import android.widget.Toast;

import androidx.fragment.app.FragmentManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import person.notfresh.readingshare.core.model.Subject;
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

    // ============ 导入导出专用 ============

    /** subject 表没有 UNIQUE(title) 约束,导入时按 name 兜底 */
    private static final String SUBJECT_DESCRIBE_FALLBACK = "";

    /**
     * 查 linkId → 它归属的所有 subject 名字。供导出时算 linkIdToSubjectNames 用。
     * @param ctx Context
     * @param linkIds 要查的链接 id 列表
     * @return key=linkId, value=subject 名字列表(已 trim);无归属的 link 不在 map 里
     */
    public static Map<Long, List<String>> getSubjectNamesByLinkIds(Context ctx, List<Long> linkIds) {
        Map<Long, List<String>> result = new HashMap<>();
        if (linkIds == null || linkIds.isEmpty()) return result;
        SubjectDao dao = new SubjectDao(ctx);
        dao.open();
        try {
            for (Long linkId : linkIds) {
                if (linkId == null) continue;
                List<Subject> subjects = dao.getSubjectsByLinkId(linkId);
                if (subjects == null || subjects.isEmpty()) continue;
                List<String> names = new ArrayList<>(subjects.size());
                for (Subject s : subjects) {
                    if (s == null || s.getTitle() == null) continue;
                    String t = s.getTitle().trim();
                    if (!t.isEmpty()) names.add(t);
                }
                if (!names.isEmpty()) result.put(linkId, names);
            }
        } finally {
            dao.close();
        }
        return result;
    }

    /**
     * 给一组 subject 名字解析出 subjectId:已存在的复用, 不存在的就新建。
     * 按名字 trim 后匹配(忽略前后空格差异)。
     * @param ctx Context
     * @param names subject 名字列表(可含 null/空字符串,会被跳过)
     * @return name → subjectId 的映射;输入的合法 name 都会有对应 id
     */
    public static Map<String, Long> resolveOrCreateSubjects(Context ctx, List<String> names) {
        Map<String, Long> result = new HashMap<>();
        if (names == null || names.isEmpty()) return result;

        SubjectDao dao = new SubjectDao(ctx);
        dao.open();
        try {
            // 加载所有 subject 建索引(name trim → id)
            Map<String, Long> nameToId = new HashMap<>();
            for (Subject s : dao.getAllSubjects()) {
                if (s == null || s.getTitle() == null) continue;
                String key = s.getTitle().trim();
                if (!key.isEmpty()) nameToId.putIfAbsent(key, s.getId());
            }

            long now = System.currentTimeMillis();
            for (String raw : names) {
                if (raw == null) continue;
                String key = raw.trim();
                if (key.isEmpty()) continue;
                if (result.containsKey(key)) continue;
                Long existing = nameToId.get(key);
                if (existing != null) {
                    result.put(key, existing);
                    continue;
                }
                Subject fresh = new Subject(key, SUBJECT_DESCRIBE_FALLBACK, now);
                long newId = dao.insertSubject(fresh);
                nameToId.put(key, newId); // 同一批内同名复用刚建的 id
                result.put(key, newId);
            }
        } finally {
            dao.close();
        }
        return result;
    }

    /**
     * 给 linkId + subjectId 创建 subject_item 关联;已存在则跳过(不抛错)。
     * 不会自动算 orderIndex — 用现有最大值+1。批量调用请自行加锁。
     * @param ctx Context
     * @param linkId 链接 id
     * @param subjectId 主题 id
     */
    public static void linkLinkToSubject(Context ctx, long linkId, long subjectId) {
        SubjectDao dao = new SubjectDao(ctx);
        dao.open();
        try {
            List<SubjectItem> existing = dao.getSubjectItemsBySubjectId(subjectId);
            for (SubjectItem si : existing) {
                if (si.getLinkId() == linkId) return; // 已存在,跳过
            }
            SubjectItem item = new SubjectItem(subjectId);
            item.setLinkId(linkId);
            int orderIndex = person.notfresh.readingshare.core.model.SubjectUtil
                    .calculateOrderIndex(existing, -1);
            item.setOrderIndex(orderIndex);
            List<SubjectItem> newItems = new ArrayList<>();
            newItems.add(item);
            dao.batchInsertSubjectItems(newItems);
        } finally {
            dao.close();
        }
    }
}
