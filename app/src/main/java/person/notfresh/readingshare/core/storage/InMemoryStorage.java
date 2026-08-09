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
