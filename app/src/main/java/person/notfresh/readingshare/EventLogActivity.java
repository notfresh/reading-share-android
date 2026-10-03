package person.notfresh.readingshare;

import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.UnderlineSpan;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import person.notfresh.readingshare.adapter.EventLogAdapter;
import person.notfresh.readingshare.eventlog.EventLogClient;
import person.notfresh.readingshare.eventlog.EventRecord;
import person.notfresh.readingshare.eventlog.SyncLogEntry;
import person.notfresh.readingshare.sync.SimpleSyncManager;

public class EventLogActivity extends AppCompatActivity {

    private static final int PAGE_SIZE = 50;

    private final List<EventRecord> events = new ArrayList<>();
    private EventLogAdapter adapter;
    private TextView emptyText;
    private TextView statusText;
    private TextView lastSyncText;
    private Button loadMoreButton;
    private Button clearButton;
    private Button syncButton;
    private String cursor;
    private boolean loading = false;
    private boolean exhausted = false;
    private ExecutorService syncExecutor;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_log);

        Toolbar toolbar = findViewById(R.id.eventlog_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        emptyText = findViewById(R.id.empty_event_text);
        statusText = findViewById(R.id.eventlog_status_text);
        lastSyncText = findViewById(R.id.eventlog_last_sync_text);
        loadMoreButton = findViewById(R.id.eventlog_load_more_button);
        clearButton = findViewById(R.id.eventlog_clear_button);
        syncButton = findViewById(R.id.eventlog_sync_button);
        RecyclerView recyclerView = findViewById(R.id.eventlog_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EventLogAdapter(events, this::showDetail);
        recyclerView.setAdapter(adapter);

        loadMoreButton.setOnClickListener(v -> loadNextPage());
        clearButton.setOnClickListener(v -> confirmClear());
        syncButton.setOnClickListener(v -> triggerManualSync());
        lastSyncText.setOnClickListener(v -> showSyncLogDialog());
        loadNextPage();
        refreshStatus();
        refreshLastSync();
    }

    /**
     * Manual sync trigger — per PROTOCOL §10.5 客户端同步触发策略 (手动按钮).
     * Pops a dialog if server is not configured. Otherwise fires push + pull
     * on a background thread and refreshes the last-sync display when done.
     */
    private void triggerManualSync() {
        if (!SimpleSyncManager.hasConfig(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("未配置同步服务端")
                    .setMessage("请先在设置中填写同步服务端地址与密钥,然后再触发同步。")
                    .setPositiveButton("好", null)
                    .show();
            return;
        }
        if (syncExecutor == null) {
            syncExecutor = Executors.newSingleThreadExecutor();
        }
        syncButton.setEnabled(false);
        syncButton.setText("同步中…");
        syncExecutor.submit(() -> {
            try {
                EventLogClient.get().pushPending("links");
                EventLogClient.get().pull("links");
            } catch (Exception ignored) {
                // best-effort; failure is captured in sync log
            } finally {
                runOnUiThread(() -> {
                    syncButton.setEnabled(true);
                    syncButton.setText("立即同步");
                    refreshLastSync();
                    refreshStatus();
                });
            }
        });
    }

    private void refreshLastSync() {
        final List<SyncLogEntry>[] recent = new List[]{Collections.emptyList()};
        new Thread(() -> {
            try {
                EventLogClient client = EventLogClient.get();
                recent[0] = client.syncLogStore().recent(1);
            } catch (Exception ignored) {
                // best-effort
            }
            runOnUiThread(() -> {
                if (recent[0].isEmpty()) {
                    lastSyncText.setText(makeClickableHint("暂无同步记录"));
                } else {
                    lastSyncText.setText(makeClickableHint(formatSyncEntry(recent[0].get(0))));
                }
            });
        }).start();
    }

    /** 给摘要末尾追加"· 点击查看完整日志"并加下划线 — 提示用户这行可点 */
    private static Spanned makeClickableHint(String summary) {
        String hint = "\n点击查看完整日志";
        String full = summary + hint;
        SpannableString ss = new SpannableString(full);
        ss.setSpan(new UnderlineSpan(), summary.length(), full.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return ss;
    }

    private static String formatSyncEntry(SyncLogEntry e) {
        StringBuilder sb = new StringBuilder();
        sb.append("上次同步 ").append(formatLocalTime(e.timestamp)).append(" ");
        sb.append(e.direction == SyncLogEntry.Direction.PUSH ? "推送" : "拉取");
        sb.append(" · ");
        if (e.success) {
            sb.append("成功");
            if (e.direction == SyncLogEntry.Direction.PUSH) {
                sb.append(" · ").append(e.sentCount).append(" 条");
            } else {
                sb.append(" · ").append(e.receivedCount).append(" 条");
            }
        } else {
            sb.append("失败");
            if (e.errorMessage != null) {
                sb.append(" · ").append(e.errorMessage);
            }
        }
        return sb.toString();
    }

    /** 弹窗:列出最近 50 条同步日志(PUSH/PULL 各一条都会显示) */
    private void showSyncLogDialog() {
        new Thread(() -> {
            final List<SyncLogEntry> entries;
            try {
                entries = EventLogClient.get().syncLogStore().recent(50);
            } catch (Exception e) {
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("同步日志")
                        .setMessage("读取失败: " + e.getMessage())
                        .setPositiveButton("好", null)
                        .show());
                return;
            }
            runOnUiThread(() -> {
                StringBuilder body = new StringBuilder();
                if (entries == null || entries.isEmpty()) {
                    body.append("暂无同步记录");
                } else {
                    for (int i = 0; i < entries.size(); i++) {
                        if (i > 0) body.append("\n\n");
                        body.append(formatSyncLogLine(entries.get(i)));
                    }
                }
                TextView tv = new TextView(this);
                tv.setText(body.toString());
                tv.setTextSize(13);
                tv.setPadding(48, 32, 48, 32);
                tv.setTextIsSelectable(true);
                ScrollView scroll = new ScrollView(this);
                scroll.addView(tv);
                new AlertDialog.Builder(this)
                        .setTitle("同步日志 · 最近 " + (entries == null ? 0 : entries.size()) + " 条")
                        .setView(scroll)
                        .setPositiveButton("关闭", null)
                        .show();
            });
        }).start();
    }

    /** 弹窗里的单行格式 — 时间 + 方向 + 成功/失败 + 条数/错误 */
    private static String formatSyncLogLine(SyncLogEntry e) {
        StringBuilder sb = new StringBuilder();
        sb.append(formatLocalTime(e.timestamp)).append("\n");
        sb.append(e.direction == SyncLogEntry.Direction.PUSH ? "推送" : "拉取");
        sb.append(" · ");
        if (e.success) {
            sb.append("成功");
            int count = e.direction == SyncLogEntry.Direction.PUSH ? e.sentCount : e.receivedCount;
            sb.append(" · ").append(count).append(" 条");
        } else {
            sb.append("失败");
            if (e.errorMessage != null) {
                sb.append(" · ").append(e.errorMessage);
            }
        }
        return sb.toString();
    }

    private static String formatLocalTime(String isoUtc) {
        // isoUtc is "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'". Display HH:mm:ss only —
        // we don't have a full date formatter available without dragging in
        // SimpleDateFormat in a UI helper. Keep it minimal.
        if (isoUtc == null || isoUtc.length() < 19) return isoUtc;
        // Find the 'T' separator and slice HH:mm:ss.
        int t = isoUtc.indexOf('T');
        if (t < 0 || t + 9 > isoUtc.length()) return isoUtc;
        return isoUtc.substring(t + 1, t + 9);
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("清空事件")
                .setMessage("清空全部事件日志？此操作不可恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (dialog, which) -> new Thread(() -> {
                    EventLogClient.get().deleteAll();
                    EventLogClient.get().resetBootstrapFlag();
                    runOnUiThread(() -> {
                        events.clear();
                        adapter.notifyDataSetChanged();
                        exhausted = false;
                        cursor = null;
                        loadMoreButton.setText("加载更多");
                        loadMoreButton.setEnabled(true);
                        emptyText.setVisibility(View.VISIBLE);
                        refreshStatus();
                    });
                }).start())
                .show();
    }

    private void refreshStatus() {
        new Thread(() -> {
            int total = EventLogClient.get().count("links");
            int loaded = events.size();
            runOnUiThread(() -> statusText.setText(
                    loaded + " / " + total + " 条"));
        }).start();
    }

    private void loadNextPage() {
        if (loading || exhausted) return;
        loading = true;
        loadMoreButton.setEnabled(false);
        loadMoreButton.setText("加载中…");
        final String useCursor = cursor;
        new Thread(() -> {
            // A 语义：拿 "cursor 之前"的 N 条按 DESC；第一页 cursor=null 拿最新写入的 N 条。
            // 按 process_time(写入序) —— 不按 event_time：各埋点传的 event_time 语义不一
            // (有的传 now()，有的传 link.timestamp)，按它排会忽新忽旧。
            List<EventRecord> page = EventLogClient.get()
                    .untilByProcessTime("links", useCursor, PAGE_SIZE);
            runOnUiThread(() -> {
                if (page.isEmpty()) {
                    exhausted = true;
                    loading = false;
                    loadMoreButton.setText("已经到底");
                    loadMoreButton.setEnabled(false);
                    return;
                }
                // page 是 DESC：page[0] 最新，page[last] 最老。append 到末尾，最新的在 list[0]。
                for (int i = 0; i < page.size(); i++) {
                    events.add(page.get(i));
                }
                cursor = page.get(page.size() - 1).getProcessTime();
                if (page.size() < PAGE_SIZE) {
                    exhausted = true;
                    loadMoreButton.setText("已经到底");
                    loadMoreButton.setEnabled(false);
                } else {
                    loadMoreButton.setText("加载更多");
                    loadMoreButton.setEnabled(true);
                }
                adapter.notifyDataSetChanged();
                emptyText.setVisibility(events.isEmpty() ? View.VISIBLE : View.GONE);
                loading = false;
                refreshStatus();
            });
        }).start();
    }

    /**
     * 字段级 diff:列出 old → data 之间变化的字段(只比一层,够看清一条链接改了什么)。
     * 任一侧不是 JSON 对象时返回 null(不显示 diff 段)。
     */
    private static String formatFieldDiff(String oldJson, String newJson) {
        if (oldJson == null || newJson == null) return null;
        try {
            JSONObject before = new JSONObject(oldJson);
            JSONObject after = new JSONObject(newJson);
            TreeSet<String> keys = new TreeSet<>();
            for (Iterator<String> it = before.keys(); it.hasNext(); ) keys.add(it.next());
            for (Iterator<String> it = after.keys(); it.hasNext(); ) keys.add(it.next());
            StringBuilder sb = new StringBuilder();
            for (String k : keys) {
                String b = before.has(k) ? String.valueOf(before.get(k)) : null;
                String a = after.has(k) ? String.valueOf(after.get(k)) : null;
                if (b != null && b.equals(a)) continue;
                sb.append("  ").append(k).append(": ")
                        .append(b == null ? "(无)" : b).append(" → ")
                        .append(a == null ? "(删)" : a).append('\n');
            }
            return sb.length() == 0 ? "  (无字段差异)\n" : sb.toString();
        } catch (JSONException e) {
            return null;
        }
    }

    private void showDetail(EventRecord item) {
        StringBuilder sb = new StringBuilder();
        sb.append("id:           ").append(item.getId()).append('\n');
        sb.append("topic:        ").append(item.getTopic()).append('\n');
        sb.append("action:       ").append(item.getAction()).append('\n');
        sb.append("entity_id:    ").append(item.getEntityId()).append('\n');
        sb.append("device_id:    ").append(item.getDeviceId()).append('\n');
        sb.append("process_time: ").append(item.getProcessTime()).append('\n');
        sb.append("event_time:   ").append(item.getEventTime()).append('\n');
        sb.append("data:         ").append(item.getData() == null ? "null" : item.getData()).append('\n');
        sb.append("old:          ").append(item.getOldJson() == null ? "null" : item.getOldJson());
        String diff = formatFieldDiff(item.getOldJson(), item.getData());
        if (diff != null) {
            sb.append("\n\n变更(old → data):\n").append(diff);
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_event_detail, null);
        TextView text = dialogView.findViewById(R.id.event_detail_text);
        text.setText(sb.toString());

        new AlertDialog.Builder(this)
                .setTitle("事件详情")
                .setView(dialogView)
                .setPositiveButton("关闭", null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
