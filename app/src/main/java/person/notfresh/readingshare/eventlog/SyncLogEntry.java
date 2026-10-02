package person.notfresh.readingshare.eventlog;

/**
 * One row in {@code eventlog_sync_log}. Records the outcome of a single
 * push or pull attempt, per PROTOCOL §10.5 客户端同步触发策略 (同步可观测性).
 */
public final class SyncLogEntry {

    /** Direction enum, mirrors {@code COL_LOG_DIRECTION} values. */
    public enum Direction {
        PUSH("push"),
        PULL("pull");

        private final String value;

        Direction(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public final long id;
    /** ISO-8601 UTC, milliseconds, "Z" suffix — same format as process_time. */
    public final String timestamp;
    public final Direction direction;
    public final boolean success;
    public final int sentCount;
    public final int receivedCount;
    /** Null when {@link #success} is true. */
    public final String errorMessage;

    public SyncLogEntry(long id, String timestamp, Direction direction,
                        boolean success, int sentCount, int receivedCount,
                        String errorMessage) {
        this.id = id;
        this.timestamp = timestamp;
        this.direction = direction;
        this.success = success;
        this.sentCount = sentCount;
        this.receivedCount = receivedCount;
        this.errorMessage = errorMessage;
    }
}
