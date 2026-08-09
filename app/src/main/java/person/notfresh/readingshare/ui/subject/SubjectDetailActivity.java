package person.notfresh.readingshare.ui.subject;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.WebViewActivity;
import person.notfresh.readingshare.adapter.SubjectItemAdapter;
import person.notfresh.readingshare.core.model.Subject;
import person.notfresh.readingshare.core.model.SubjectItem;
import person.notfresh.readingshare.core.model.SubjectUtil;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.db.SubjectDao;
import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.ui.subject.AddSubjectItemDialog;
import person.notfresh.readingshare.ui.subject.SubjectArchivedItemsDialog;
import person.notfresh.readingshare.core.storage.KeyValueStorage;
import person.notfresh.readingshare.util.android.SharedPreferencesStorage;

/**
 * 主题详情页Activity
 */
public class SubjectDetailActivity extends AppCompatActivity implements
        SubjectItemAdapter.OnSubjectItemClickListener,
        SubjectItemAdapter.OnSubjectItemEditListener,
        SubjectItemAdapter.OnSubjectItemActionListener {
    private static final String TAG = "SubjectDetailActivity";
    public static final String EXTRA_SUBJECT_ID = "subject_id";

    private RecyclerView recyclerView;
    private android.widget.TextView textEmpty;
    private SubjectItemAdapter adapter;
    private SubjectDao subjectDao;
    private LinkDao linkDao;
    private Subject subject;
    private long subjectId;
    private ItemTouchHelper itemTouchHelper;
    private List<SubjectItem> currentSubjectItems; // 保存当前主题项列表用于构建 context_ids
    private SubjectEntryManager subjectEntryManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_detail);

        // 获取主题ID
        subjectId = getIntent().getLongExtra(EXTRA_SUBJECT_ID, -1);
        if (subjectId == -1) {
            Toast.makeText(this, "主题ID无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 设置Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        subjectDao = new SubjectDao(this);
        subjectDao.open();
        linkDao = new LinkDao(this);
        linkDao.open();

        // 加载主题
        subject = subjectDao.getSubjectById(subjectId);
        if (subject == null) {
            Toast.makeText(this, "主题不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 设置标题
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(subject.getTitle());
        }

        recyclerView = findViewById(R.id.recycler_view);
        textEmpty = findViewById(R.id.text_empty);
        adapter = new SubjectItemAdapter(this);
        adapter.setOnSubjectItemClickListener(this);
        adapter.setOnSubjectItemEditListener(this);
        adapter.setOnSubjectItemActionListener(this);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // 设置拖拽排序
        setupDragAndDrop();

        loadSubjectItems();

        // 初始化 SubjectEntryManager 并保存当前查看的主题
        KeyValueStorage storage = new SharedPreferencesStorage(this, "subject_entry_prefs");
        subjectEntryManager = new SubjectEntryManager(storage);
        subjectEntryManager.saveLastViewedSubject(subjectId);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (adapter != null) {
            adapter.close();
        }
        if (subjectDao != null) {
            subjectDao.close();
        }
        if (linkDao != null) {
            linkDao.close();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.subject_detail_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_add_item) {
            showAddSubjectItemDialog();
            return true;
        } else if (item.getItemId() == R.id.action_subject_settings) {
            showSubjectSettingsDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAddSubjectItemDialog() {
        AddSubjectItemDialog dialog = AddSubjectItemDialog.newInstance(subjectId);
        dialog.setOnSubjectItemSavedListener(item -> {
            // 刷新列表
            loadSubjectItems();
            Toast.makeText(this, "主题项添加成功", Toast.LENGTH_SHORT).show();
        });
        dialog.show(getSupportFragmentManager(), "AddSubjectItemDialog");
    }

    private void loadSubjectItems() {
        // 重新从数据库加载主题（包含主题项）
        subject = subjectDao.getSubjectById(subjectId);
        if (subject != null) {
            currentSubjectItems = subject.getSubItems();
            adapter.setItems(currentSubjectItems);
            updateEmptyState(currentSubjectItems);
            Log.d(TAG, "加载了 " + currentSubjectItems.size() + " 个主题项");
        } else {
            currentSubjectItems = null;
            updateEmptyState(null);
        }
    }

    private void updateEmptyState(List<SubjectItem> items) {
        if (textEmpty == null) {
            return;
        }
        boolean isEmpty = items == null || items.isEmpty();
        textEmpty.setVisibility(isEmpty ? android.view.View.VISIBLE : android.view.View.GONE);
        recyclerView.setVisibility(isEmpty ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    @Override
    public void onSubjectItemClick(SubjectItem item) {
        // 如果有链接ID，构造 context_ids 并启动 WebViewActivity
        if (item.getLinkId() != null && item.getLinkId() > 0) {
            openWebViewWithContext(item);
        } else {
            // 没有链接，显示编辑对话框
            showEditSubjectItemDialog(item);
        }
    }

    @Override
    public void onSubjectItemEdit(SubjectItem item) {
        // 编辑操作：直接显示编辑对话框
        showEditSubjectItemDialog(item);
    }

    /**
     * 启动 WebViewActivity 并传递上下文信息
     */
    private void openWebViewWithContext(SubjectItem clickedItem) {
        if (currentSubjectItems == null || currentSubjectItems.isEmpty()) {
            return;
        }

        // 收集所有有链接的 SubjectItem 的 linkId
        java.util.ArrayList<Long> contextIdsList = new java.util.ArrayList<>();
        int clickedIndex = -1;

        for (int i = 0; i < currentSubjectItems.size(); i++) {
            SubjectItem item = currentSubjectItems.get(i);
            if (item.getLinkId() != null && item.getLinkId() > 0) {
                contextIdsList.add(item.getLinkId());
                if (clickedIndex == -1 && item.getId() == clickedItem.getId()) {
                    clickedIndex = contextIdsList.size() - 1;
                }
            }
        }

        if (contextIdsList.size() <= 1 || clickedIndex == -1) {
            // 只有一个或没有链接，直接打开编辑对话框
            showEditSubjectItemDialog(clickedItem);
            return;
        }

        // 获取链接的 URL
        LinkItem linkItem = linkDao.getLinkById(clickedItem.getLinkId());
        if (linkItem == null || linkItem.getUrl() == null) {
            Toast.makeText(this, "无法获取链接", Toast.LENGTH_SHORT).show();
            return;
        }

        // 构建 context_ids 数组
        long[] contextIds = new long[contextIdsList.size()];
        for (int i = 0; i < contextIdsList.size(); i++) {
            contextIds[i] = contextIdsList.get(i);
        }

        // 启动 WebViewActivity
        Intent intent = new Intent(this, WebViewActivity.class);
        intent.putExtra("url", linkItem.getUrl());
        intent.putExtra("context_ids", contextIds);
        intent.putExtra("context_index", clickedIndex);
        startActivity(intent);
    }

    private void showEditSubjectItemDialog(SubjectItem item) {
        AddSubjectItemDialog dialog = AddSubjectItemDialog.newInstance(subjectId, item);
        dialog.setOnSubjectItemSavedListener(editedItem -> {
            // 刷新列表
            loadSubjectItems();
            Toast.makeText(this, "主题项更新成功", Toast.LENGTH_SHORT).show();
        });
        dialog.show(getSupportFragmentManager(), "EditSubjectItemDialog");
    }

    /**
     * 设置拖拽排序
     */
    private void setupDragAndDrop() {
        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                 @NonNull RecyclerView.ViewHolder viewHolder,
                                 @NonNull RecyclerView.ViewHolder target) {
                int fromPos = viewHolder.getAdapterPosition();
                int toPos = target.getAdapterPosition();

                // 获取当前列表
                List<SubjectItem> items = adapter.getItems();
                if (fromPos < 0 || fromPos >= items.size() || toPos < 0 || toPos >= items.size()) {
                    return false;
                }

                // 交换位置
                SubjectItem draggedItem = items.get(fromPos);
                items.remove(fromPos);
                items.add(toPos, draggedItem);
                adapter.notifyItemMoved(fromPos, toPos);

                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // 不需要实现
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView,
                                 @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                
                // 拖拽结束，按照当前列表顺序重新分配 orderIndex 并保存
                // 注意：不要先排序，因为 onMove 已经改变了列表顺序，直接按当前顺序分配即可
                List<SubjectItem> items = adapter.getItems();
                
                // 按照当前列表顺序（已经是拖拽后的顺序）重新分配 orderIndex
                for (int i = 0; i < items.size(); i++) {
                    items.get(i).setOrderIndex(i * SubjectUtil.ORDER_INTERVAL);
                }
                
                // 保存到数据库
                // 注意：updateSubjectItem 内部已经有事务处理，但为了批量更新的原子性，可以优化
                int successCount = 0;
                for (SubjectItem item : items) {
                    if (subjectDao.updateSubjectItem(item)) {
                        successCount++;
                    }
                }
                
                Log.d(TAG, "拖拽排序完成，已更新 " + successCount + "/" + items.size() + " 个主题项的 orderIndex");
                
                // 验证：重新加载一次，确保顺序正确
                // loadSubjectItems(); // 可选：如果需要立即验证，可以取消注释
            }
        };

        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(recyclerView);
    }

    @Override
    public void onCollectLink(SubjectItem item, LinkItem linkItem) {
        // 检查链接是否已经在主页存在
        List<LinkItem> existingLinks = linkDao.getAllLinks();
        boolean linkExists = false;
        for (LinkItem existing : existingLinks) {
            if (existing.getUrl() != null && existing.getUrl().equals(linkItem.getUrl())) {
                linkExists = true;
                break;
            }
        }

        if (!linkExists) {
            // 如果不存在，创建新链接
            linkDao.insertLink(linkItem);
            Toast.makeText(this, "链接已收录到主页", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "链接已存在于主页", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDeleteSubjectItem(SubjectItem item) {
        // 删除主题项（包括图片文件）
        boolean deleted = subjectDao.deleteSubjectItem(item.getId());
        if (deleted) {
            loadSubjectItems();
            Toast.makeText(this, "主题项已删除", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onArchiveSubjectItem(SubjectItem item) {
        long archivedAt = System.currentTimeMillis();
        boolean archived = subjectDao.archiveSubjectItem(item.getId(), archivedAt);
        if (archived) {
            item.setArchived(true);
            item.setArchivedAt(archivedAt);
            if (adapter != null) {
                adapter.removeItem(item);
            }
            Toast.makeText(this, "已归档，可在设置-已归档中查看", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "归档失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRestoreSubjectItem(SubjectItem item) {
        boolean restored = subjectDao.restoreSubjectItem(item.getId());
        if (restored) {
            item.setArchived(false);
            item.setArchivedAt(0);
            loadSubjectItems();
            Toast.makeText(this, "已还原", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "还原失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRefreshItems() {
        loadSubjectItems();
    }

    private void showSubjectSettingsDialog() {
        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_subject_settings, null);
        android.widget.Button btnArchived = view.findViewById(R.id.btn_archived);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("设置")
                .setView(view)
                .setNegativeButton("关闭", null)
                .create();

        btnArchived.setOnClickListener(v -> {
            dialog.dismiss();
            showArchivedItemsDialog();
        });

        dialog.show();
    }

    private void showArchivedItemsDialog() {
        SubjectArchivedItemsDialog dialog = SubjectArchivedItemsDialog.newInstance(subjectId);
        dialog.setOnItemsRestoredListener(() -> loadSubjectItems());
        dialog.show(getSupportFragmentManager(), "SubjectArchivedItemsDialog");
    }
}

