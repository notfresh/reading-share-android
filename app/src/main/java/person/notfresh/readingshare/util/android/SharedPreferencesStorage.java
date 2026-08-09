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
