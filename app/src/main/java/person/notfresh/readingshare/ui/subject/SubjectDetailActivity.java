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
import person.notfresh.readingshare.adapter.SubjectItemAdapter;
import person.notfresh.readingshare.core.model.Subject;
import person.notfresh.readingshare.core.model.SubjectItem;
import person.notfresh.readingshare.core.model.SubjectUtil;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.db.SubjectDao;
import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.ui.subject.AddSubjectItemDialog;

/**
 * 主题详情页Activity
 */
public class SubjectDetailActivity extends AppCompatActivity implements 
        SubjectItemAdapter.OnSubjectItemClickListener,
        SubjectItemAdapter.OnSubjectItemActionListener {
    private static final String TAG = "SubjectDetailActivity";
    public static final String EXTRA_SUBJECT_ID = "subject_id";

    private RecyclerView recyclerView;
    private SubjectItemAdapter adapter;
    private SubjectDao subjectDao;
    private LinkDao linkDao;
    private Subject subject;
    private long subjectId;
    private ItemTouchHelper itemTouchHelper;

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
        adapter = new SubjectItemAdapter(this);
        adapter.setOnSubjectItemClickListener(this);
        adapter.setOnSubjectItemActionListener(this);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // 设置拖拽排序
        setupDragAndDrop();

        loadSubjectItems();
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
            List<SubjectItem> items = subject.getSubItems();
            adapter.setItems(items);
            Log.d(TAG, "加载了 " + items.size() + " 个主题项");
        }
    }

    @Override
    public void onSubjectItemClick(SubjectItem item) {
        showEditSubjectItemDialog(item);
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
                
                // 拖拽结束，重新计算 orderIndex 并保存
                List<SubjectItem> items = adapter.getItems();
                SubjectUtil.sortByOrderIndex(items); // 先按当前 orderIndex 排序
                
                // 重新分配 orderIndex
                for (int i = 0; i < items.size(); i++) {
                    items.get(i).setOrderIndex(i * SubjectUtil.ORDER_INTERVAL);
                }
                
                // 保存到数据库
                for (SubjectItem item : items) {
                    subjectDao.updateSubjectItem(item);
                }
                
                Log.d(TAG, "拖拽排序完成，已更新 orderIndex");
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
    public void onRefreshItems() {
        loadSubjectItems();
    }
}

