package person.notfresh.readingshare;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import person.notfresh.readingshare.adapter.LinksHistoryAdapter;
import person.notfresh.readingshare.db.DbConnection;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.model.LinkHistoryItem;

public class LinksHistoryActivity extends AppCompatActivity {
    private final List<LinkHistoryItem> historyItems = new ArrayList<>();
    private LinkDao linkDao;
    private LinksHistoryAdapter adapter;
    private TextView emptyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_links_history);

        Toolbar toolbar = findViewById(R.id.history_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        linkDao = new LinkDao(DbConnection.get(this).writable());
        RecyclerView recyclerView = findViewById(R.id.history_recycler_view);
        emptyText = findViewById(R.id.empty_history_text);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LinksHistoryAdapter(historyItems, new LinksHistoryAdapter.Listener() {
            @Override
            public void onHistoryClick(LinkHistoryItem item) {
                Intent intent = new Intent(LinksHistoryActivity.this, WebViewActivity.class);
                intent.putExtra("url", item.getUrl());
                startActivity(intent);
            }

            @Override
            public void onDeleteClick(LinkHistoryItem item) {
                confirmDelete(item);
            }
        });
        recyclerView.setAdapter(adapter);

        Button clearButton = findViewById(R.id.clear_history_button);
        clearButton.setOnClickListener(v -> confirmClear());
        loadHistory();
    }

    private void loadHistory() {
        new Thread(() -> {
            List<LinkHistoryItem> loaded = linkDao.getAllLinkHistory();
            runOnUiThread(() -> {
                historyItems.clear();
                historyItems.addAll(loaded);
                adapter.notifyDataSetChanged();
                emptyText.setVisibility(historyItems.isEmpty() ? View.VISIBLE : View.GONE);
            });
        }).start();
    }

    private void confirmDelete(LinkHistoryItem item) {
        new AlertDialog.Builder(this)
                .setTitle("删除历史记录")
                .setMessage(item.getTitle())
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> new Thread(() -> {
                    boolean deleted = linkDao.deleteLinkHistory(item.getId());
                    runOnUiThread(() -> {
                        if (deleted) {
                            historyItems.remove(item);
                            adapter.notifyDataSetChanged();
                            emptyText.setVisibility(historyItems.isEmpty() ? View.VISIBLE : View.GONE);
                        } else {
                            Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }).start())
                .show();
    }

    private void confirmClear() {
        if (historyItems.isEmpty()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("清空浏览历史")
                .setMessage("确定清空全部浏览历史吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (dialog, which) -> new Thread(() -> {
                    linkDao.clearLinkHistory();
                    runOnUiThread(() -> {
                        historyItems.clear();
                        adapter.notifyDataSetChanged();
                        emptyText.setVisibility(View.VISIBLE);
                    });
                }).start())
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
