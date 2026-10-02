package person.notfresh.readingshare.eventlog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * HTTP implementation of {@link EventLogPusher}. Sends
 * {@code POST /events} with a {@code {"events": [...]}} batch body
 * as specified in PROTOCOL.md §4.1. The server detects the batch
 * shape from the body, so a batch of one event is identical to a
 * single-event post from the wire's point of view.
 *
 * <p>Threading: this implementation BLOCKS on the HTTP round-trip. Callers
 * MUST run it on a background thread. Error handling follows §10.5 PUSH
 * 流程: any non-2xx response is reported via {@link PushResult#success}=false
 * so the caller's cursor can stay put for the next retry (server-side
 * idempotency by event id handles duplicate re-push).</p>
 */
public final class HttpEventLogPusher implements EventLogPusher {

    /** Per §4.1.1, server caps batch length at 1000. */
    private static final int MAX_BATCH = 1000;
    /** Network read timeout — generous default for mobile. */
    private static final int TIMEOUT_MS = 30_000;

    private final String baseUrl;
    private final String secret;

    public HttpEventLogPusher(String baseUrl, String secret) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new EventLogException("baseUrl is empty");
        }
        if (secret == null || secret.isEmpty()) {
            throw new EventLogException("secret is empty");
        }
        // Strip trailing slash to keep URL joining predictable.
        this.baseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        this.secret = secret;
    }

    @Override
    public PushResult push(String topic, List<EventRecord> batch) throws EventLogException {
        if (topic == null || topic.isEmpty()) {
            throw new EventLogException("topic is empty");
        }
        if (batch == null || batch.isEmpty()) {
            throw new EventLogException("batch is empty");
        }
        if (batch.size() > MAX_BATCH) {
            throw new EventLogException(
                    "batch size " + batch.size() + " exceeds limit " + MAX_BATCH);
        }

        String body = encodeRequest(topic, batch);
        HttpURLConnection conn = null;
        try {
            URL url = new URL(this.baseUrl + "/events");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", secret);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            String responseBody = readBody(conn, status);

            if (status == 200 || status == 201) {
                return parseSuccess(batch, responseBody);
            }
            // 4xx/5xx: caller treats as whole-batch failure; cursor stays.
            String err = "HTTP " + status + ": " + truncate(responseBody, 200);
            return PushResult.fail(batch.size(), err);

        } catch (IOException e) {
            return PushResult.fail(batch.size(), "io: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String encodeRequest(String topic, List<EventRecord> batch) {
        try {
            JSONArray arr = new JSONArray();
            for (EventRecord r : batch) {
                JSONObject o = new JSONObject();
                o.put("id", r.getId());
                o.put("device_id", r.getDeviceId());
                o.put("topic", r.getTopic().isEmpty() ? topic : r.getTopic());
                o.put("entity_id", r.getEntityId());
                o.put("action", r.getAction().name().toLowerCase());
                o.put("event_time", r.getEventTime());
                o.put("process_time", r.getProcessTime());
                String data = r.getData();
                if (data == null) {
                    o.put("data", JSONObject.NULL);
                } else {
                    // data is stored as a JSON string in the local row.
                    // Pass through as-is; server treats it as opaque payload.
                    o.put("data", data);
                }
                arr.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("events", arr);
            return root.toString();
        } catch (JSONException e) {
            throw new EventLogException("failed to encode batch", e);
        }
    }

    private static PushResult parseSuccess(List<EventRecord> batch, String body) {
        // Server response: {"results":[{index,status,id,process_time},...],
        //                  "total_new":N,"total_duplicate":K}
        // We only need sentCount + newCount + duplicateCount + lastPushedProcessTime.
        try {
            JSONObject root = new JSONObject(body);
            int totalNew = root.optInt("total_new", batch.size());
            int totalDup = root.optInt("total_duplicate", 0);
            // lastPushedProcessTime: use last event in the batch (caller's
            // responsibility to send them in process_time ASC order).
            String lastPt = batch.get(batch.size() - 1).getProcessTime();
            return PushResult.ok(batch.size(), totalNew, totalDup, lastPt);
        } catch (JSONException e) {
            // 2xx but malformed body — caller should treat as soft success
            // (server wrote the rows) but flag for debugging.
            String lastPt = batch.get(batch.size() - 1).getProcessTime();
            return PushResult.ok(batch.size(), batch.size(), 0, lastPt);
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
