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