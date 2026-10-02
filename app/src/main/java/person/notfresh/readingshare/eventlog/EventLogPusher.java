package person.notfresh.readingshare.eventlog;

import java.util.List;

/**
 * Pushes a batch of locally-stored events to a remote server.
 *
 * Implementations decide transport (HTTP / IPC / in-memory test stub) and
 * threading — callers MUST NOT assume the call is non-blocking or cheap.
 * Typical usage: call from a background thread / coroutine / executor.
 *
 * See PROTOCOL.md §4.1.1 POST /events/batch and §10.5 PUSH 流程.
 */
public interface EventLogPusher {

    /**
     * Outcome of a single batch push attempt.
     *
     * <p>Server-side behavior per §4.1.1: a non-2xx response means the batch
     * was rejected (validation failure or some per-event write failed). The
     * client treats this as "整批失败 → cursor 不动" — see §10.5.</p>
     */
    final class PushResult {
        /** Server returned 2xx; cursor may advance to {@code lastPushedProcessTime}. */
        public final boolean success;
        /** How many events were in the batch sent to the server. */
        public final int sentCount;
        /** Server-reported count of new inserts (sum of {@code total_new}). */
        public final int newCount;
        /** Server-reported count of idempotent hits (sum of {@code total_duplicate}). */
        public final int duplicateCount;
        /** Cursor value the client may persist: last pushed event's process_time. */
        public final String lastPushedProcessTime;
        /** Human-readable error message when {@link #success} is false. May be null. */
        public final String errorMessage;

        private PushResult(boolean success, int sentCount, int newCount,
                           int duplicateCount, String lastPushedProcessTime,
                           String errorMessage) {
            this.success = success;
            this.sentCount = sentCount;
            this.newCount = newCount;
            this.duplicateCount = duplicateCount;
            this.lastPushedProcessTime = lastPushedProcessTime;
            this.errorMessage = errorMessage;
        }

        public static PushResult ok(int sentCount, int newCount,
                                     int duplicateCount, String lastPushedProcessTime) {
            return new PushResult(true, sentCount, newCount, duplicateCount,
                    lastPushedProcessTime, null);
        }

        public static PushResult fail(int sentCount, String errorMessage) {
            return new PushResult(false, sentCount, 0, 0, null, errorMessage);
        }
    }

    /**
     * Push a batch of events for the given topic to the server.
     *
     * @param topic event topic
     * @param batch events to send, ordered by process_time ASC (matches the
     *              local cursor walk). Caller is responsible for selecting
     *              and sizing the batch (e.g. 100 events).
     * @return outcome; never null
     * @throws EventLogException for unrecoverable client-side problems
     *         (e.g. config missing). Network / HTTP errors are surfaced
     *         via {@link PushResult#success} = false instead of throwing,
     *         so the caller can keep its cursor intact.
     */
    PushResult push(String topic, List<EventRecord> batch) throws EventLogException;
}
