# 主题模块入口记忆功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现主题模块入口记忆功能，进入主题模块时默认打开上次查看的主题，支持配置

**Architecture:** 核心逻辑与视图层分离，通过 KeyValueStorage 接口实现可测试性，通用 KV 存储可供其他模块复用

**Tech Stack:** Android, Java, SharedPreferences, JUnit

---

## 文件结构

```
app/src/main/java/person/notfresh/readingshare/
├── core/
│   └── storage/
│       ├── KeyValueStorage.java          # 通用KV存储接口
│       └── InMemoryStorage.java         # 测试用内存实现
├── util/
│   └── android/
│       └── SharedPreferencesStorage.java # Android平台实现
└── ui/subject/
    ├── SubjectEntryManager.java          # 核心逻辑
    ├── SubjectEntrySettingsDialog.java   # 设置对话框
    └── SubjectFragment.java             # 添加设置按钮、入口逻辑
```

---

## 实现任务

### Task 1: 创建通用 KeyValueStorage 接口

**Files:**
- Create: `app/src/main/java/person/notfresh/readingshare/core/storage/KeyValueStorage.java`

- [ ] **Step 1: 创建 KeyValueStorage 接口**

```java
package person.notfresh.readingshare.core.storage;

/**
 * 通用 Key-Value 存储接口
 * 可被项目中多个模块复用
 */
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

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/person/notfresh/readingshare/core/storage/KeyValueStorage.java
git commit -m "feat(storage): add KeyValueStorage interface for generic KV storage"
```

---

### Task 2: 创建 SharedPreferencesStorage 实现

**Files:**
- Create: `app/src/main/java/person/notfresh/readingshare/util/android/SharedPreferencesStorage.java`

- [ ] **Step 1: 创建 SharedPreferencesStorage 实现**

```java
package person.notfresh.readingshare.util.android;

import android.content.Context;
import android.content.SharedPreferences;

import person.notfresh.readingshare.core.storage.KeyValueStorage;

/**
 * Android SharedPreferences 实现
 */
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
    
    @Override
    public long getLong(String key, long defaultValue) {
        return prefs.getLong(key, defaultValue);
    }
    
    @Override
    public void putLong(String key, long value) {
        prefs.edit().putLong(key, value).apply();
    }
    
    @Override
    public int getInt(String key, int defaultValue) {
        return prefs.getInt(key, defaultValue);
    }
    
    @Override
    public void putInt(String key, int value) {
        prefs.edit().putInt(key, value).apply();
    }
    
    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        return prefs.getBoolean(key, defaultValue);
    }
    
    @Override
    public void putBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }
    
    @Override
    public void remove(String key) {
        prefs.edit().remove(key).apply();
    }
    
    @Override
    public void clear() {
        prefs.edit().clear().apply();
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/person/notfresh/readingshare/util/android/SharedPreferencesStorage.java
git commit -m "feat(storage): add SharedPreferencesStorage implementation"
```

---

### Task 3: 创建 InMemoryStorage 测试实现

**Files:**
- Create: `app/src/main/java/person/notfresh/readingshare/core/storage/InMemoryStorage.java`

- [ ] **Step 1: 创建 InMemoryStorage 测试实现**

```java
package person.notfresh.readingshare.core.storage;

import java.util.HashMap;
import java.util.Map;

/**
 * 内存存储实现，用于单元测试
 */
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
    
    @Override
    public long getLong(String key, long defaultValue) {
        Object value = data.get(key);
        if (value instanceof Long) {
            return (Long) value;
        } else if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return defaultValue;
    }
    
    @Override
    public void putLong(String key, long value) {
        data.put(key, value);
    }
    
    @Override
    public int getInt(String key, int defaultValue) {
        Object value = data.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
    
    @Override
    public void putInt(String key, int value) {
        data.put(key, value);
    }
    
    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = data.get(key);
        return value instanceof Boolean ? (Boolean) value : defaultValue;
    }
    
    @Override
    public void putBoolean(String key, boolean value) {
        data.put(key, value);
    }
    
    @Override
    public void remove(String key) {
        data.remove(key);
    }
    
    @Override
    public void clear() {
        data.clear();
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/person/notfresh/readingshare/core/storage/InMemoryStorage.java
git commit -m "test(storage): add InMemoryStorage for unit testing"
```

---

### Task 4: 创建 SubjectEntryManager 核心逻辑

**Files:**
- Create: `app/src/main/java/person/notfresh/readingshare/ui/subject/SubjectEntryManager.java`

- [ ] **Step 1: 创建 SubjectEntryManager 核心逻辑类**

```java
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
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/person/notfresh/readingshare/ui/subject/SubjectEntryManager.java
git commit -m "feat(subject): add SubjectEntryManager for entry memory feature"
```

---

### Task 5: 创建设置对话框布局和代码

**Files:**
- Create: `app/src/main/res/layout/dialog_subject_entry_settings.xml`
- Create: `app/src/main/java/person/notfresh/readingshare/ui/subject/SubjectEntrySettingsDialog.java`

- [ ] **Step 1: 创建设置对话框布局**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="24dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="进入时默认显示"
        android:textSize="16sp"
        android:textStyle="bold"
        android:layout_marginBottom="12dp" />

    <RadioGroup
        android:id="@+id/radio_group_entry_preference"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <RadioButton
            android:id="@+id/radio_detail"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="主题详情"
            android:padding="8dp" />

        <RadioButton
            android:id="@+id/radio_list"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="主题列表"
            android:padding="8dp" />
    </RadioGroup>

    <View
        android:layout_width="match_parent"
        android:layout_height="1dp"
        android:background="#e0e0e0"
        android:layout_marginVertical="16dp" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="记忆的主题"
        android:textSize="16sp"
        android:textStyle="bold"
        android:layout_marginBottom="12dp" />

    <Spinner
        android:id="@+id/spinner_memory_subject"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:background="@android:drawable/btn_dropdown" />

    <TextView
        android:id="@+id/text_memory_hint"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="保存后，进入主题模块将自动打开此主题"
        android:textSize="12sp"
        android:textColor="#666666"
        android:layout_marginTop="8dp" />

</LinearLayout>
```

- [ ] **Step 2: 创建设置对话框类**

```java
package person.notfresh.readingshare.ui.subject;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.core.model.Subject;
import person.notfresh.readingshare.core.storage.KeyValueStorage;
import person.notfresh.readingshare.db.SubjectDao;
import person.notfresh.readingshare.util.android.SharedPreferencesStorage;

/**
 * 主题入口设置对话框
 */
public class SubjectEntrySettingsDialog extends Dialog {
    
    private SubjectEntryManager manager;
    private SubjectDao subjectDao;
    
    private RadioGroup radioGroupPreference;
    private RadioButton radioDetail;
    private RadioButton radioList;
    private Spinner spinnerMemorySubject;
    
    private List<Subject> subjects = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter;
    private List<String> subjectNames = new ArrayList<>();
    
    public interface OnSettingsSavedListener {
        void onSettingsSaved();
    }
    
    private OnSettingsSavedListener listener;
    
    public SubjectEntrySettingsDialog(@NonNull Context context) {
        super(context);
        init(context);
    }
    
    private void init(Context context) {
        // 初始化存储和Manager
        KeyValueStorage storage = new SharedPreferencesStorage(context, "subject_entry_prefs");
        manager = new SubjectEntryManager(storage);
        
        // 初始化数据库
        subjectDao = new SubjectDao(context);
        subjectDao.open();
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_subject_entry_settings);
        
        initViews();
        loadSettings();
        loadSubjects();
    }
    
    private void initViews() {
        radioGroupPreference = findViewById(R.id.radio_group_entry_preference);
        radioDetail = findViewById(R.id.radio_detail);
        radioList = findViewById(R.id.radio_list);
        spinnerMemorySubject = findViewById(R.id.spinner_memory_subject);
        
        // 设置入口偏好RadioGroup监听
        radioGroupPreference.setOnCheckedChangeListener((group, checkedId) -> {
            // 如果选择列表，隐藏记忆主题选项
            if (checkedId == R.id.radio_list) {
                spinnerMemorySubject.setVisibility(View.GONE);
                findViewById(R.id.text_memory_hint).setVisibility(View.GONE);
            } else {
                spinnerMemorySubject.setVisibility(View.VISIBLE);
                findViewById(R.id.text_memory_hint).setVisibility(View.VISIBLE);
            }
        });
        
        // 初始化下拉选择器
        spinnerAdapter = new ArrayAdapter<>(getContext(), 
                android.R.layout.simple_spinner_item, subjectNames);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMemorySubject.setAdapter(spinnerAdapter);
        
        // 设置确定和取消按钮
        findViewById(R.id.btn_save).setOnClickListener(v -> saveSettings());
        findViewById(R.id.btn_cancel).setOnClickListener(v -> dismiss());
    }
    
    private void loadSettings() {
        // 加载入口偏好
        SubjectEntryManager.EntryPreference preference = manager.getEntryPreference();
        if (preference == SubjectEntryManager.EntryPreference.LIST) {
            radioList.setChecked(true);
        } else {
            radioDetail.setChecked(true);
        }
    }
    
    private void loadSubjects() {
        subjects = subjectDao.getAllSubjects();
        
        subjectNames.clear();
        subjectNames.add("上次查看");
        for (Subject subject : subjects) {
            subjectNames.add(subject.getTitle());
        }
        spinnerAdapter.notifyDataSetChanged();
        
        // 加载记忆的主题选择
        long memorySubjectId = manager.getMemorySubjectId();
        if (memorySubjectId == SubjectEntryManager.VALUE_LAST_VIEWED) {
            spinnerMemorySubject.setSelection(0);
        } else {
            for (int i = 0; i < subjects.size(); i++) {
                if (subjects.get(i).getId() == memorySubjectId) {
                    spinnerMemorySubject.setSelection(i + 1);
                    break;
                }
            }
        }
    }
    
    private void saveSettings() {
        // 保存入口偏好
        SubjectEntryManager.EntryPreference preference;
        if (radioList.isChecked()) {
            preference = SubjectEntryManager.EntryPreference.LIST;
        } else {
            preference = SubjectEntryManager.EntryPreference.DETAIL;
        }
        manager.setEntryPreference(preference);
        
        // 保存记忆的主题
        int selectedPosition = spinnerMemorySubject.getSelectedItemPosition();
        if (selectedPosition == 0) {
            manager.setMemorySubjectId(SubjectEntryManager.VALUE_LAST_VIEWED);
        } else if (selectedPosition > 0 && selectedPosition <= subjects.size()) {
            manager.setMemorySubjectId(subjects.get(selectedPosition - 1).getId());
        }
        
        if (listener != null) {
            listener.onSettingsSaved();
        }
        dismiss();
    }
    
    public void setOnSettingsSavedListener(OnSettingsSavedListener listener) {
        this.listener = listener;
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (subjectDao != null) {
            subjectDao.close();
        }
    }
}
```

- [ ] **Step 3: 更新布局添加按钮**

在 `dialog_subject_entry_settings.xml` 末尾添加按钮：

```xml
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="end"
        android:layout_marginTop="24dp">

        <Button
            android:id="@+id/btn_cancel"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="取消"
            style="?android:attr/borderlessButtonStyle" />

        <Button
            android:id="@+id/btn_save"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="保存"
            android:layout_marginStart="8dp" />
    </LinearLayout>
```

- [ ] **Step 4: 提交**

```bash
git add app/src/main/res/layout/dialog_subject_entry_settings.xml
git add app/src/main/java/person/notfresh/readingshare/ui/subject/SubjectEntrySettingsDialog.java
git commit -m "feat(subject): add entry settings dialog"
```

---

### Task 6: 修改 SubjectFragment 添加设置按钮和入口逻辑

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/ui/subject/SubjectFragment.java`
- Modify: `app/src/main/res/menu/subject_menu.xml`

- [ ] **Step 1: 在菜单添加设置按钮**

在 `subject_menu.xml` 中添加：

```xml
<item
    android:id="@+id/action_subject_entry_settings"
    android:icon="@android:drawable/ic_menu_preferences"
    android:title="设置"
    app:showAsAction="ifRoom" />
```

- [ ] **Step 2: 修改 SubjectFragment 添加设置按钮处理和入口逻辑**

在 `SubjectFragment.java` 中：

1. 添加导入：
```java
import person.notfresh.readingshare.core.storage.KeyValueStorage;
import person.notfresh.readingshare.util.android.SharedPreferencesStorage;
```

2. 添加成员变量：
```java
private SubjectEntryManager subjectEntryManager;
```

3. 在 onCreate 中初始化：
```java
// 初始化 SubjectEntryManager
KeyValueStorage storage = new SharedPreferencesStorage(requireContext(), "subject_entry_prefs");
subjectEntryManager = new SubjectEntryManager(storage);
```

4. 在 onOptionsItemSelected 中添加设置按钮处理：
```java
} else if (item.getItemId() == R.id.action_subject_entry_settings) {
    showEntrySettingsDialog();
    return true;
}
```

5. 添加 showEntrySettingsDialog 方法：
```java
private void showEntrySettingsDialog() {
    SubjectEntrySettingsDialog dialog = new SubjectEntrySettingsDialog(requireContext());
    dialog.setOnSettingsSavedListener(() -> {
        // 设置保存后，重新检查入口逻辑
        checkAndNavigateToEntry();
    });
    dialog.show();
}
```

6. 添加 checkAndNavigateToEntry 方法：
```java
private void checkAndNavigateToEntry() {
    Long targetSubjectId = subjectEntryManager.getDefaultEntryTarget();
    if (targetSubjectId != null && targetSubjectId > 0) {
        // 跳转到主题详情
        Intent intent = new Intent(requireContext(), SubjectDetailActivity.class);
        intent.putExtra(SubjectDetailActivity.EXTRA_SUBJECT_ID, targetSubjectId);
        startActivity(intent);
    }
    // 否则显示主题列表（默认行为）
}
```

7. 在 onResume 中调用入口检查：
```java
@Override
public void onResume() {
    super.onResume();
    checkAndNavigateToEntry();
}
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/person/notfresh/readingshare/ui/subject/SubjectFragment.java
git add app/src/main/res/menu/subject_menu.xml
git commit -m "feat(subject): add settings button and entry navigation logic"
```

---

### Task 7: 修改 SubjectDetailActivity 保存上次查看

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/ui/subject/SubjectDetailActivity.java`

- [ ] **Step 1: 添加保存上次查看逻辑**

1. 添加导入：
```java
import person.notfresh.readingshare.core.storage.KeyValueStorage;
import person.notfresh.readingshare.util.android.SharedPreferencesStorage;
```

2. 添加成员变量：
```java
private SubjectEntryManager subjectEntryManager;
```

3. 在 onCreate 中初始化：
```java
// 初始化 SubjectEntryManager
KeyValueStorage storage = new SharedPreferencesStorage(this, "subject_entry_prefs");
subjectEntryManager = new SubjectEntryManager(storage);

// 保存当前查看的主题
subjectEntryManager.saveLastViewedSubject(subjectId);
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/person/notfresh/readingshare/ui/subject/SubjectDetailActivity.java
git commit -m "feat(subject): save last viewed subject in SubjectDetailActivity"
```

---

### Task 8: 创建单元测试

**Files:**
- Create: `app/src/test/java/person/notfresh/readingshare/ui/subject/SubjectEntryManagerTest.java`

- [ ] **Step 1: 创建 SubjectEntryManager 单元测试**

```java
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
```

- [ ] **Step 2: 运行测试验证**

```bash
./gradlew test --tests SubjectEntryManagerTest
```

- [ ] **Step 3: 提交**

```bash
git add app/src/test/java/person/notfresh/readingshare/ui/subject/SubjectEntryManagerTest.java
git commit -m "test(subject): add SubjectEntryManager unit tests"
```

---

## 实施检查清单

在完成所有任务后，确认以下功能正常工作：

- [ ] KeyValueStorage 接口已创建
- [ ] SharedPreferencesStorage Android 实现已创建
- [ ] InMemoryStorage 测试实现已创建
- [ ] SubjectEntryManager 核心逻辑已创建并可测试
- [ ] 设置对话框 UI 已创建
- [ ] SubjectFragment 标题栏显示设置按钮
- [ ] 点击设置按钮显示配置对话框
- [ ] 进入主题模块时根据配置自动跳转
- [ ] 离开主题详情页时自动保存上次查看
- [ ] 单元测试全部通过
