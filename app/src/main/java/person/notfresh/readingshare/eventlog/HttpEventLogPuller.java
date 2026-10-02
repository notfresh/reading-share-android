package person.notfresh.readingshare.eventlog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP implementation of {@link EventLogPuller}. Sends
 * {@code GET /events?since=...&topic=...&limit=...} as specified in
 * PROTOCOL.md §4.2.
 *
 * <p>Threading: this implementation BLOCKS. Callers MUST run it on a
 * background thread.</p>
 */
public final class HttpEventLogPuller implements EventLogPuller {

    /** Per §4.2, server caps limit at 10000. */
    private static final int MAX_LIMIT = 10000;
    private static final int TIMEOUT_MS = 30_000;

    private final String baseUrl;
    private final String secret;

    public HttpEventLogPuller(String baseUrl, String secret) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new EventLogException("baseUrl is empty");
        }
        if (secret == null || secret.isEmpty()) {
            throw new EventLogException("secret is empty");
        }
        // 防御性 trim — 防止上游传入的 secret 末尾有换行/空格，
        // 进 HTTP Authorization 头会触发 IllegalArgumentException
        this.secret = secret.trim();
        this.baseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
    }

    @Override
    public PullResult pull(String topic, String sinceProcessTime, int limit) {
        if (topic == null || topic.isEmpty()) {
            throw new EventLogException("topic is empty");
        }
        if (limit <= 0 || limit > MAX_LIMIT) {
            throw new EventLogException(
                    "limit must be in [1, " + MAX_LIMIT + "], got " + limit);
        }

        String urlStr = buildUrl(topic, sinceProcessTime, limit);
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Authorization", secret);

            int status = conn.getResponseCode();
            String responseBody = readBody(conn, status);

            if (status == 200) {
                return parseSuccess(responseBody);
            }
            return PullResult.fail("HTTP " + status + ": " + truncate(responseBody, 200));

        } catch (IOException e) {
            return PullResult.fail("io: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String buildUrl(String topic, String sinceProcessTime, int limit) {
        StringBuilder sb = new StringBuilder(this.baseUrl)
                .append("/events?limit=").append(limit)
                .append("&topic=").append(URLEncoder.encode(topic, StandardCharsets.UTF_8));
        if (sinceProcessTime != null && !sinceProcessTime.isEmpty()) {
            sb.append("&since=").append(URLEncoder.encode(sinceProcessTime, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static PullResult parseSuccess(String body) {
        List<EventRecord> events = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(body);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                EventAction action = EventAction.valueOf(
                        o.optString("action", "create").toUpperCase());
                String dataJson = o.isNull("data") ? null : o.optString("data", null);
                events.add(new EventRecord(
                        o.getString("id"),
                        o.optString("topic", ""),
                        o.optString("process_time", ""),
                        o.optString("event_time", ""),
                        o.optString("device_id", ""),
                        o.optString("entity_id", ""),
                        action,
                        dataJson
                ));
            }
            return PullResult.ok(events);
        } catch (JSONException e) {
            return PullResult.fail("malformed response: " + e.getMessage());
        }
    }

    private static String readBody(HttpURLConnection conn, int status) {
        InputStream is = null;
        try {
            is = (status >= 400) ? conn.getErrorStream() : conn.getInputStream();
            if (is == null) {
                return "";
            }
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
        } catch (IOException e) {
            return "";
        } finally {
            if (is != null) {
                try { is.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
