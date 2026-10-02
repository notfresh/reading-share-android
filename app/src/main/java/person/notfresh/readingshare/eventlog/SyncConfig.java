package person.notfresh.readingshare.eventlog;

/**
 * Minimal configuration for remote event-log sync.
 *
 * <p>Per PROTOCOL §10.5, the cursor key MUST include the server URL so that
 * switching servers does not lose sync progress. {@code baseUrl} here is
 * used both as the HTTP endpoint prefix and as the namespace component of
 * cursor keys.</p>
 *
 * <p>{@code secret} is the shared authentication token compared verbatim
 * against the server's {@code EVENT_LOG_SECRET} (PROTOCOL §2).</p>
 */
public final class SyncConfig {

    private final String baseUrl;
    private final String secret;

    public SyncConfig(String baseUrl, String secret) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new EventLogException("baseUrl is empty");
        }
        if (secret == null || secret.isEmpty()) {
            throw new EventLogException("secret is empty");
        }
        this.baseUrl = baseUrl;
        this.secret = secret;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String secret() {
        return secret;
    }
}
