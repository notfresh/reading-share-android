# 主题模块入口记忆功能设计

## 1. 需求概述

用户进入主题模块时，默认打开上次查看的主题，而非每次都显示主题列表。需要支持配置功能，允许用户选择默认行为。

## 2. 功能需求

### 2.1 入口配置
- **默认行为**：进入主题模块 → 直接打开上次查看的主题详情
- **备选行为**：进入主题模块 → 显示主题列表（兼容旧行为）

### 2.2 设置功能
- 设置入口：主题列表标题栏的"⚙️ 设置"按钮
- 可配置项：
  - 进入时默认显示：主题列表 / 主题详情
  - 记忆的主题：上次查看 / 指定具体主题

### 2.3 记忆逻辑
- 用户离开主题详情页时，自动记录当前查看的主题ID
- 用户再次进入主题模块时，自动打开记录的主题

## 3. 架构设计

### 3.1 核心原则
- 核心逻辑与视图层分离
- 核心逻辑可独立测试
- 通用KV存储可供其他模块复用

### 3.2 组件划分

```
┌─────────────────────────────────────────────────────────┐
│                        UI 层                          │
│    SubjectFragment  │  SubjectDetailActivity          │
│    (设置对话框)                                           │
└────────────────────────┬────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────┐
│            核心逻辑层 (SubjectEntryManager)           │
│    - 记忆/恢复逻辑                                      │
│    - 配置管理                                           │
│    - 无 Android 视图依赖                                 │
│    - 依赖通用 KeyValueStorage 接口                     │
└────────────────────────┬────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────┐
│              通用 KV 存储层 (KeyValueStorage)         │
│    <<interface>> - 可被项目中其他模块复用              │
│    + getString / putString                            │
│    + getLong / putLong                                │
│    + getInt / putInt                                  │
│    + getBoolean / putBoolean                          │
└────────────────────────┬────────────────────────────────┘
                         │
            ┌────────────┴────────────┐
            ▼                         ▼
┌─────────────────────┐    ┌─────────────────────────┐
│ SharedPreferences  │    │   InMemoryStorage      │
│ Storage (Android)  │    │   (Test Implementation)│
└─────────────────────┘    └─────────────────────────┘
```

### 3.3 通用 KV 存储抽象层

创建通用的 Key-Value 存储接口，可供项目中其他模块复用：

```java
// 通用 Key-Value 存储接口（放置在 core 或 util 包中）
public interface KeyValueStorage {
    String getString(String key, String defaultValue);
    void putString(String key, String value);
    
    long getLong(String key, long defaultValue);
    void putLong(String key, long value);
    
    int getInt(String key, int defaultValue);
    void putInt(String key, int value);
    
    boolean getBoolean(String key, boolean defaultValue);
    void putBoolean(String key, boolean value);
    
    void remove(String key);
    void clear();
}
```

```java
// Android SharedPreferences 实现（放置在 android util 包中）
public class SharedPreferencesStorage implements KeyValueStorage {
    private final SharedPreferences prefs;
    
    public SharedPreferencesStorage(Context context, String prefName) {
        prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE);
    }
    
    @Override
    public String getString(String key, String defaultValue) {
        return prefs.getString(key, defaultValue);
    }
    
    @Override
    public void putString(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }
    
    // ... 其他方法类似实现
}
```

### 3.4 SubjectEntryManager 类设计

```java
// 核心逻辑类 - 依赖通用 KeyValueStorage 接口
public class SubjectEntryManager {
    // 入口偏好
    public enum EntryPreference { LIST, DETAIL }

    // Key 常量（带前缀避免冲突）
    public static final String KEY_ENTRY_PREFERENCE = "subject_entry_preference";
    public static final String KEY_MEMORY_SUBJECT_ID = "subject_memory_subject_id";
    public static final String KEY_LAST_SUBJECT_ID = "subject_last_subject_id";

    private final KeyValueStorage storage;

    // 构造函数，注入存储实现（便于测试时注入 mock）
    public SubjectEntryManager(KeyValueStorage storage) {
        this.storage = storage;
    }

    // 获取默认入口目标
    // @return 要打开的主题ID，如果返回 null 则打开主题列表
    public Long getDefaultEntryTarget() {
        EntryPreference pref = getEntryPreference();
        if (pref == EntryPreference.LIST) {
            return null; // 显示列表
        }
        
        // DETAIL 模式：获取要打开的主题ID
        long memorySubjectId = storage.getLong(KEY_MEMORY_SUBJECT_ID, -1);
        if (memorySubjectId == -1) {
            // "上次查看"模式：返回上次查看的主题ID
            return storage.getLong(KEY_LAST_SUBJECT_ID, -1);
        } else {
            // 指定主题模式：返回指定的主题ID
            return memorySubjectId;
        }
    }

    // 保存上次查看的主题
    public void saveLastViewedSubject(long subjectId) {
        storage.putLong(KEY_LAST_SUBJECT_ID, subjectId);
    }

    // 获取入口偏好
    public EntryPreference getEntryPreference() {
        String value = storage.getString(KEY_ENTRY_PREFERENCE, "detail");
        return "list".equalsIgnoreCase(value) ? EntryPreference.LIST : EntryPreference.DETAIL;
    }

    // 设置入口偏好
    public void setEntryPreference(EntryPreference preference) {
        storage.putString(KEY_ENTRY_PREFERENCE, preference.name().toLowerCase());
    }

    // 获取记忆的主题ID（-1 表示"上次查看"）
    public long getMemorySubjectId() {
        return storage.getLong(KEY_MEMORY_SUBJECT_ID, -1);
    }

    // 设置记忆的主题ID
    public void setMemorySubjectId(long subjectId) {
        storage.putLong(KEY_MEMORY_SUBJECT_ID, subjectId);
    }
}
```

## 4. UI 改动

### 4.1 SubjectFragment
- 标题栏添加设置按钮（MenuItem）
- 点击设置按钮 → 显示配置对话框
- onResume 中判断入口偏好，决定是否跳转到主题详情

### 4.2 设置对话框
- 布局：dialog_subject_entry_settings.xml
- 选项：
  - 单选组：进入时默认显示（主题列表 / 主题详情）
  - 下拉选择：记忆的主题（上次查看 / 各主题列表）

### 4.3 SubjectDetailActivity
- 在 onDestroy 或 onPause 中调用 manager.saveLastViewedSubject()

## 5. 数据存储

### 5.1 Key 定义

Key 常量已在 SubjectEntryManager 中定义（见 3.4 节），使用 `subject_` 前缀避免与其他模块冲突。

### 5.2 使用方式

```java
// 在 UI 层创建存储实现并注入
KeyValueStorage storage = new SharedPreferencesStorage(requireContext(), "subject_entry_prefs");
SubjectEntryManager manager = new SubjectEntryManager(storage);

// UI 层调用
Long targetId = manager.getDefaultEntryTarget();
if (targetId != null && targetId > 0) {
    // 跳转到主题详情
    Intent intent = new Intent(requireContext(), SubjectDetailActivity.class);
    intent.putExtra(SubjectDetailActivity.EXTRA_SUBJECT_ID, targetId);
    startActivity(intent);
} else {
    // 显示主题列表（默认行为）
}
```

### 5.3 其他模块复用

其他模块可以使用相同的 KeyValueStorage 接口创建自己的配置管理类：

```java
// 例如：标签模块的配置管理
public class TagPreferencesManager {
    private static final String KEY_TAG_SORT_ORDER = "tag_sort_order";
    private final KeyValueStorage storage;
    
    public TagPreferencesManager(KeyValueStorage storage) {
        this.storage = storage;
    }
    
    public void setSortOrder(String order) {
        storage.putString(KEY_TAG_SORT_ORDER, order);
    }
}
```

## 6. 测试策略

### 6.1 单元测试

SubjectEntryManager 通过 KeyValueStorage 接口依赖存储，可使用 Mock 或内存实现进行纯 Java 单元测试：

```java
// 内存存储实现（用于测试）
public class InMemoryStorage implements KeyValueStorage {
    private final Map<String, Object> data = new HashMap<>();
    
    @Override
    public String getString(String key, String defaultValue) {
        Object value = data.get(key);
        return value instanceof String ? (String) value : defaultValue;
    }
    
    @Override
    public void putString(String key, String value) {
        data.put(key, value);
    }
    
    // ... 其他方法类似实现
}

// 示例测试
@Test
public void testGetDefaultEntryTarget_detailWithLastSubject() {
    // 准备：使用内存存储，配置为 DETAIL 模式，上次查看主题 ID = 123
    InMemoryStorage storage = new InMemoryStorage();
    storage.putString(KEY_ENTRY_PREFERENCE, "detail");
    storage.putLong(KEY_MEMORY_SUBJECT_ID, -1);  // 上次查看
    storage.putLong(KEY_LAST_SUBJECT_ID, 123);
    
    SubjectEntryManager manager = new SubjectEntryManager(storage);
    
    // 验证：应返回上次查看的主题 ID
    assertEquals(Long.valueOf(123), manager.getDefaultEntryTarget());
}

@Test
public void testGetDefaultEntryTarget_listPreference() {
    // 准备：配置为 LIST 模式
    InMemoryStorage storage = new InMemoryStorage();
    storage.putString(KEY_ENTRY_PREFERENCE, "list");
    
    SubjectEntryManager manager = new SubjectEntryManager(storage);
    
    // 验证：应返回 null（显示列表）
    assertNull(manager.getDefaultEntryTarget());
}
```

### 6.2 测试覆盖

- getDefaultEntryTarget() 在不同配置下的返回值
- saveLastViewedSubject() 正确保存
- setEntryPreference() / getEntryPreference() 的配对
- getMemorySubjectId() / setMemorySubjectId() 的配对
- 边界情况：未设置偏好、未记录上次主题等
