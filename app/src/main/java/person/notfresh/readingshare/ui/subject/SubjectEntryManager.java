package person.notfresh.readingshare.ui.subject;

import person.notfresh.readingshare.core.storage.KeyValueStorage;

/**
 * 主题入口管理器
 * 负责记忆用户上次查看的主题，实现入口配置功能
 * 核心逻辑通过 KeyValueStorage 接口实现，可独立测试
 */
public class SubjectEntryManager {

    /** 入口偏好 */
    public enum EntryPreference {
        LIST,   // 显示主题列表
        DETAIL  // 显示主题详情
    }

    // Key 常量（带前缀避免冲突）
    public static final String KEY_ENTRY_PREFERENCE = "subject_entry_preference";
    public static final String KEY_MEMORY_SUBJECT_ID = "subject_memory_subject_id";
    public static final String KEY_LAST_SUBJECT_ID = "subject_last_subject_id";

    // 默认值常量
    public static final long VALUE_LAST_VIEWED = -1;  // 表示"上次查看"

    private final KeyValueStorage storage;

    public SubjectEntryManager(KeyValueStorage storage) {
        this.storage = storage;
    }

    /**
     * 获取默认入口目标
     * @return 要打开的主题ID，如果返回 null 或 -1 则显示主题列表
     */
    public Long getDefaultEntryTarget() {
        EntryPreference pref = getEntryPreference();
        if (pref == EntryPreference.LIST) {
            return null; // 显示列表
        }

        // DETAIL 模式：获取要打开的主题ID
        long memorySubjectId = storage.getLong(KEY_MEMORY_SUBJECT_ID, VALUE_LAST_VIEWED);
        if (memorySubjectId == VALUE_LAST_VIEWED) {
            // "上次查看"模式：返回上次查看的主题ID
            long lastSubjectId = storage.getLong(KEY_LAST_SUBJECT_ID, -1);
            return lastSubjectId > 0 ? lastSubjectId : null;
        } else if (memorySubjectId > 0) {
            // 指定主题模式：返回指定的主题ID
            return memorySubjectId;
        }

        return null; // 默认显示列表
    }

    /**
     * 保存上次查看的主题
     * @param subjectId 主题ID
     */
    public void saveLastViewedSubject(long subjectId) {
        if (subjectId > 0) {
            storage.putLong(KEY_LAST_SUBJECT_ID, subjectId);
        }
    }

    public Long getLastViewedSubjectId() {
        long subjectId = storage.getLong(KEY_LAST_SUBJECT_ID, -1);
        return subjectId > 0 ? subjectId : null;
    }

    /**
     * 获取入口偏好
     */
    public EntryPreference getEntryPreference() {
        String value = storage.getString(KEY_ENTRY_PREFERENCE, EntryPreference.DETAIL.name().toLowerCase());
        return "list".equalsIgnoreCase(value) ? EntryPreference.LIST : EntryPreference.DETAIL;
    }

    /**
     * 设置入口偏好
     * @param preference 入口偏好
     */
    public void setEntryPreference(EntryPreference preference) {
        storage.putString(KEY_ENTRY_PREFERENCE, preference.name().toLowerCase());
    }

    /**
     * 获取记忆的主题ID
     * @return -1 表示"上次查看"
     */
    public long getMemorySubjectId() {
        return storage.getLong(KEY_MEMORY_SUBJECT_ID, VALUE_LAST_VIEWED);
    }

    /**
     * 设置记忆的主题ID
     * @param subjectId -1 表示"上次查看"
     */
    public void setMemorySubjectId(long subjectId) {
        storage.putLong(KEY_MEMORY_SUBJECT_ID, subjectId);
    }
}