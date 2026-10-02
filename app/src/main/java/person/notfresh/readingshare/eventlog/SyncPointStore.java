package person.notfresh.readingshare.eventlog;

/**
 * Persistent key-value store for sync-point cursors.
 *
 * Cursors are client-private state. The protocol requires that cursor keys
 * include server_url so that switching servers does not lose sync progress
 * (see PROTOCOL.md §10.5 "游标 key 必须包含 server_url"). Typical keys:
 *
 * <ul>
 *   <li>{@code push:<server_url>:<topic>} → last locally-pushed process_time</li>
 *   <li>{@code pull:<server_url>:<topic>} → last server-side process_time pulled</li>
 * </ul>
 *
 * Implementations decide on actual storage (SQLite, prefs, file, ...).
 * This interface is intentionally minimal so it can be swapped or stubbed
 * in tests.
 */
public interface SyncPointStore {

    /**
     * @return stored value, or {@code null} if the key has never been set.
     */
    String get(String key);

    /**
     * Store a value for the given key, overwriting any previous value.
     * @throws EventLogException if key or value is null/empty.
     */
    void set(String key, String value);

    /**
     * Remove a key. No-op if the key does not exist.
     */
    void delete(String key);
}
