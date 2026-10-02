package person.notfresh.readingshare.eventlog;

import java.util.List;

/**
 * Persistent log of sync attempts (push / pull). Used to satisfy
 * PROTOCOL §10.5 同步可观测性 — clients must record every sync result
 * so users and debugging can inspect what happened.
 */
public interface SyncLogStore {

    /**
     * Append a new entry. Caller supplies the timestamp string (ISO-8601
     * UTC, "Z" suffix) so batch writes can share a single timestamp
     * generation moment.
     */
    void add(SyncLogEntry.Direction direction, String timestamp,
             boolean success, int sentCount, int receivedCount,
             String errorMessage);

    /** Return the most recent {@code limit} entries, newest first. */
    List<SyncLogEntry> recent(int limit);
}
