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

import person.notfresh.readingshare.adapter.EventLogAdapter;
import person.notfresh.readingshare.eventlog.EventLogClient;
import person.notfresh.readingshare.eventlog.EventRecord;

public class EventLogActivity extends AppCompatActivity {

    private static final int PAGE_SIZE = 50;

    private final List<EventRecord> events = new ArrayList<>();
    private EventLogAdapter adapter;
    private TextView emptyText;
    private TextView statusText;
    private Button loadMoreButton;
    private Button clearButton;
    private String cursor;
    private boolean loading = false;
    private boolean exhausted = false;

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
        loadMoreButton = findViewById(R.id.eventlog_load_more_button);
        clearButton = findViewById(R.id.eventlog_clear_button);
        RecyclerView recyclerView = findViewById(R.id.eventlog_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EventLogAdapter(events, this::showDetail);
        recyclerView.setAdapter(adapter);

        loadMoreButton.setOnClickListener(v -> loadNextPage());
        clearButton.setOnClickListener(v -> confirmClear());
        loadNextPage();
        refreshStatus();
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
