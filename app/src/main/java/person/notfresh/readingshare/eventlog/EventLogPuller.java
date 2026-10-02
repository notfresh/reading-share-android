package person.notfresh.readingshare.eventlog;

import java.util.List;

/**
 * Pulls a window of events from a remote server.
 *
 * Used to implement PROTOCOL §5.3 增量同步 (PULL side of §10.5). Callers
 * loop, advancing the cursor with {@code batch[-1].process_time} after
 * each successful call, until the returned list is shorter than the
 * requested limit (signal that there are no more events).
 *
 * <p>Threading: implementations BLOCK on the HTTP round-trip. Callers
 * MUST run them on a background thread.</p>
 */
public interface EventLogPuller {

    /**
     * Outcome of a pull attempt.
     */
    final class PullResult {
        /** Whether the server returned 2xx and we got a usable batch. */
        public final boolean success;
        /** The events returned by the server (already parsed). Empty list on failure. */
        public final List<EventRecord> events;
        /** Human-readable error message when {@link #success} is false. May be null. */
        public final String errorMessage;

        private PullResult(boolean success, List<EventRecord> events, String errorMessage) {
            this.success = success;
            this.events = events;
            this.errorMessage = errorMessage;
        }

        public static PullResult ok(List<EventRecord> events) {
            return new PullResult(true, events, null);
        }

        public static PullResult fail(String errorMessage) {
            return new PullResult(false, java.util.Collections.emptyList(), errorMessage);
        }
    }

    /**
     * Fetch one batch of events strictly newer than {@code sinceProcessTime}
     * for the given topic. Caller is responsible for the loop and cursor
     * advancement (§5.3).
     *
     * @param topic event topic
     * @param sinceProcessTime cursor; pass {@code null} for cold start
     * @param limit max number of events to return (clamped by server to 10000)
     * @return outcome; never null
     * @throws EventLogException for unrecoverable client-side problems
     *         (e.g. config missing). Network / HTTP errors are surfaced
     *         via {@link PullResult#success}=false instead.
     */
    PullResult pull(String topic, String sinceProcessTime, int limit) throws EventLogException;
}
