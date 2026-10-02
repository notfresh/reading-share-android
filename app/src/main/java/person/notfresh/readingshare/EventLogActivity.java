package person.notfresh.readingshare;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
                    lastSyncText.setText("暂无同步记录");
                } else {
                    lastSyncText.setText(formatSyncEntry(recent[0].get(0)));
                }
            });
        }).start();
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
            // A 语义：until() 拿 "cursor 之前"的 N 条按 DESC；第一页 cursor=null 拿最晚 N 条
            List<EventRecord> page = EventLogClient.get().until("links", useCursor, PAGE_SIZE);
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
                cursor = page.get(page.size() - 1).getEventTime();
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

    private void showDetail(EventRecord item) {
        StringBuilder sb = new StringBuilder();
        sb.append("id:           ").append(item.getId()).append('\n');
        sb.append("topic:        ").append(item.getTopic()).append('\n');
        sb.append("action:       ").append(item.getAction()).append('\n');
        sb.append("entity_id:    ").append(item.getEntityId()).append('\n');
        sb.append("device_id:    ").append(item.getDeviceId()).append('\n');
        sb.append("process_time: ").append(item.getProcessTime()).append('\n');
        sb.append("event_time:   ").append(item.getEventTime()).append('\n');
        sb.append("data:         ").append(item.getData() == null ? "null" : item.getData());

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
