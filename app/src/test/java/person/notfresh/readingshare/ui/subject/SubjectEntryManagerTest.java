package person.notfresh.readingshare.ui.subject;

import org.junit.Before;
import org.junit.Test;

import person.notfresh.readingshare.core.storage.InMemoryStorage;
import person.notfresh.readingshare.core.storage.KeyValueStorage;

import static org.junit.Assert.*;

/**
 * SubjectEntryManager 单元测试
 */
public class SubjectEntryManagerTest {

    private KeyValueStorage storage;
    private SubjectEntryManager manager;

    @Before
    public void setUp() {
        storage = new InMemoryStorage();
        manager = new SubjectEntryManager(storage);
    }

    @Test
    public void testGetDefaultEntryTarget_detailWithLastSubject() {
        // 准备：配置为 DETAIL 模式，上次查看主题 ID = 123
        storage.putString(SubjectEntryManager.KEY_ENTRY_PREFERENCE, "detail");
        storage.putLong(SubjectEntryManager.KEY_MEMORY_SUBJECT_ID, SubjectEntryManager.VALUE_LAST_VIEWED);
        storage.putLong(SubjectEntryManager.KEY_LAST_SUBJECT_ID, 123);

        // 验证：应返回上次查看的主题 ID
        assertEquals(Long.valueOf(123), manager.getDefaultEntryTarget());
    }

    @Test
    public void testGetDefaultEntryTarget_listPreference() {
        // 准备：配置为 LIST 模式
        storage.putString(SubjectEntryManager.KEY_ENTRY_PREFERENCE, "list");

        // 验证：应返回 null（显示列表）
        assertNull(manager.getDefaultEntryTarget());
    }

    @Test
    public void testGetDefaultEntryTarget_detailWithSpecificSubject() {
        // 准备：配置为 DETAIL 模式，指定主题 ID = 456
        storage.putString(SubjectEntryManager.KEY_ENTRY_PREFERENCE, "detail");
        storage.putLong(SubjectEntryManager.KEY_MEMORY_SUBJECT_ID, 456);

        // 验证：应返回指定的主题 ID
        assertEquals(Long.valueOf(456), manager.getDefaultEntryTarget());
    }

    @Test
    public void testGetDefaultEntryTarget_noLastSubject() {
        // 准备：配置为 DETAIL 模式，但没有查看过任何主题
        storage.putString(SubjectEntryManager.KEY_ENTRY_PREFERENCE, "detail");
        storage.putLong(SubjectEntryManager.KEY_MEMORY_SUBJECT_ID, SubjectEntryManager.VALUE_LAST_VIEWED);
        // 没有设置 KEY_LAST_SUBJECT_ID

        // 验证：应返回 null（显示列表）
        assertNull(manager.getDefaultEntryTarget());
    }

    @Test
    public void testSaveLastViewedSubject() {
        // 保存上次查看的主题
        manager.saveLastViewedSubject(789);

        // 验证：应该正确保存
        assertEquals(789, storage.getLong(SubjectEntryManager.KEY_LAST_SUBJECT_ID, 0));
    }

    @Test
    public void testSaveLastViewedSubject_invalidId() {
        // 保存无效的主题ID（<=0）
        manager.saveLastViewedSubject(-1);

        // 验证：不应该保存
        assertEquals(0, storage.getLong(SubjectEntryManager.KEY_LAST_SUBJECT_ID, 0));
    }

    @Test
    public void testSetAndGetEntryPreference() {
        // 设置为 LIST
        manager.setEntryPreference(SubjectEntryManager.EntryPreference.LIST);
        assertEquals(SubjectEntryManager.EntryPreference.LIST, manager.getEntryPreference());

        // 设置为 DETAIL
        manager.setEntryPreference(SubjectEntryManager.EntryPreference.DETAIL);
        assertEquals(SubjectEntryManager.EntryPreference.DETAIL, manager.getEntryPreference());
    }

    @Test
    public void testSetAndGetMemorySubjectId() {
        // 设置为指定主题
        manager.setMemorySubjectId(111);
        assertEquals(111, manager.getMemorySubjectId());

        // 设置为"上次查看"
        manager.setMemorySubjectId(SubjectEntryManager.VALUE_LAST_VIEWED);
        assertEquals(SubjectEntryManager.VALUE_LAST_VIEWED, manager.getMemorySubjectId());
    }

    @Test
    public void testDefaultValues() {
        // 验证默认值
        assertEquals(SubjectEntryManager.EntryPreference.DETAIL, manager.getEntryPreference());
        assertEquals(SubjectEntryManager.VALUE_LAST_VIEWED, manager.getMemorySubjectId());
        assertNull(manager.getDefaultEntryTarget());
    }
}
