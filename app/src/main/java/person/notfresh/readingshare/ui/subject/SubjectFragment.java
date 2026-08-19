package person.notfresh.readingshare.ui.subject;

import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import person.notfresh.readingshare.R;
import person.notfresh.readingshare.MainActivity;
import person.notfresh.readingshare.WebViewActivity;
import person.notfresh.readingshare.adapter.SubjectItemAdapter;
import person.notfresh.readingshare.core.model.Subject;
import person.notfresh.readingshare.core.model.SubjectItem;
import person.notfresh.readingshare.core.model.SubjectUtil;
import person.notfresh.readingshare.core.storage.KeyValueStorage;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.db.DbConnection;
import person.notfresh.readingshare.db.SubjectDao;
import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.util.android.SharedPreferencesStorage;

public class SubjectFragment extends Fragment implements SubjectItemAdapter.OnSubjectItemClickListener,
        SubjectItemAdapter.OnSubjectItemEditListener, SubjectItemAdapter.OnSubjectItemActionListener {
    private RecyclerView recyclerView;
    private TextView textEmpty;
    private SubjectItemAdapter adapter;
    private SubjectDao subjectDao;
    private LinkDao linkDao;
    private Subject subject;
    private List<SubjectItem> currentItems;
    private SubjectEntryManager entryManager;
    private boolean listDialogShown;

    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setHasOptionsMenu(true);
        KeyValueStorage storage = new SharedPreferencesStorage(requireContext(), "subject_entry_prefs");
        entryManager = new SubjectEntryManager(storage);
    }

    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        View root = inflater.inflate(R.layout.fragment_subject, container, false);
        recyclerView = root.findViewById(R.id.recycler_view);
        textEmpty = root.findViewById(R.id.text_empty);
        SQLiteDatabase sharedDb = DbConnection.get(requireContext().getApplicationContext()).writable();
        subjectDao = new SubjectDao(sharedDb);
        subjectDao.open();
        linkDao = new LinkDao(sharedDb);
        linkDao.open();
        adapter = new SubjectItemAdapter(requireContext());
        adapter.setOnSubjectItemClickListener(this);
        adapter.setOnSubjectItemEditListener(this);
        adapter.setOnSubjectItemActionListener(this);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        setupDragAndDrop();
        return root;
    }

    @Override public void onResume() {
        super.onResume();
        if (subject != null) loadSubject(subject.getId());
        else if (!listDialogShown && subjectDao != null) openRememberedSubjectOrList();
    }

    @Override public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.subject_detail_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_go_home) {
            ((MainActivity) requireActivity()).navigateTo(R.id.nav_home);
            return true;
        }
        if (item.getItemId() == R.id.action_add_item) { showAddSubjectItemDialog(); return true; }
        if (item.getItemId() == R.id.action_show_subject_list) { showSubjectListDialog(); return true; }
        if (item.getItemId() == R.id.action_random_subject) { pickRandomSubject(); return true; }
        if (item.getItemId() == R.id.action_subject_settings) { showSubjectSettingsDialog(); return true; }
        return super.onOptionsItemSelected(item);
    }

    @Override public void onDestroyView() {
        if (adapter != null) adapter.close();
        // subjectDao / linkDao 共享 DbConnection 连接,不关闭(由进程结束统一回收)
        adapter = null;
        subjectDao = null;
        linkDao = null;
        super.onDestroyView();
    }

    private void openRememberedSubjectOrList() {
        Long id = entryManager.getLastViewedSubjectId();
        if (id == null || !loadSubject(id)) showSubjectListDialog();
    }

    private boolean loadSubject(long id) {
        return loadSubject(id, true);
    }

    private boolean loadSubject(long id, boolean rememberLastViewed) {
        Subject loaded = subjectDao.getSubjectById(id);
        if (loaded == null) {
            subject = null;
            updateToolbarTitle(null);
            updateEmptyState(null);
            return false;
        }
        subject = loaded;
        currentItems = loaded.getSubItems();
        adapter.setItems(currentItems);
        updateEmptyState(currentItems);
        updateToolbarTitle(loaded);
        if (rememberLastViewed) {
            entryManager.saveLastViewedSubject(id);
        }
        return true;
    }

    /**
     * 随机选择一个主题并切换过去,不污染「记忆上次主题」的语义。
     */
    private void pickRandomSubject() {
        if (subjectDao == null) return;
        List<Subject> all = subjectDao.getAllSubjects();
        if (all == null || all.isEmpty()) {
            Toast.makeText(requireContext(), "暂无可用主题", Toast.LENGTH_SHORT).show();
            return;
        }
        Subject picked = all.get(new java.util.Random().nextInt(all.size()));
        loadSubject(picked.getId(), false);
        Toast.makeText(requireContext(), "随机选择:" + picked.getName(), Toast.LENGTH_SHORT).show();
    }

    private void showSubjectListDialog() {
        if (listDialogShown || !isAdded()) return;
        listDialogShown = true;
        SubjectListDialog dialog = SubjectListDialog.newInstance(subject == null ? -1 : subject.getId());
        dialog.setOnDismissListener(this::onSubjectListDismissed);
        dialog.setOnSubjectSelectedListener(id -> { listDialogShown = false; loadSubject(id); });
        dialog.setOnSubjectDeletedListener(id -> {
            if (subject != null && subject.getId() == id) {
                subject = null;
                currentItems = null;
                entryManager.setMemorySubjectId(SubjectEntryManager.VALUE_LAST_VIEWED);
                updateToolbarTitle(null);
                updateEmptyState(null);
                recyclerView.post(() -> { listDialogShown = false; showSubjectListDialog(); });
            }
        });
        dialog.show(getParentFragmentManager(), "SubjectListDialog");
    }

    void onSubjectListDismissed() { listDialogShown = false; }

    private void updateToolbarTitle(Subject value) {
        Toolbar toolbar = requireActivity().findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setTitle(value == null ? getString(R.string.menu_subject) : value.getTitle());
    }

    private void updateEmptyState(List<SubjectItem> items) {
        boolean empty = items == null || items.isEmpty();
        textEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void showAddSubjectItemDialog() {
        if (subject == null) { showSubjectListDialog(); return; }
        AddSubjectItemDialog dialog = AddSubjectItemDialog.newInstance(subject.getId());
        dialog.setOnSubjectItemSavedListener(item -> loadSubject(subject.getId()));
        dialog.show(getParentFragmentManager(), "AddSubjectItemDialog");
    }

    @Override public void onSubjectItemClick(SubjectItem item) {
        if (item.getLinkId() == null || item.getLinkId() <= 0) showEditSubjectItemDialog(item);
        else openWebViewWithContext(item);
    }

    @Override public void onSubjectItemEdit(SubjectItem item) { showEditSubjectItemDialog(item); }

    private void openWebViewWithContext(SubjectItem clicked) {
        ArrayList<Long> ids = new ArrayList<>();
        int index = -1;
        for (SubjectItem item : currentItems) if (item.getLinkId() != null && item.getLinkId() > 0) {
            ids.add(item.getLinkId());
            if (item.getId() == clicked.getId()) index = ids.size() - 1;
        }
        LinkItem link = linkDao.getLinkById(clicked.getLinkId());
        if (link == null || link.getUrl() == null) {
            Toast.makeText(requireContext(), "无法获取链接", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(requireContext(), WebViewActivity.class);
        intent.putExtra("url", link.getUrl());
        if (ids.size() > 1 && index >= 0) {
            long[] contextIds = new long[ids.size()];
            for (int i = 0; i < ids.size(); i++) contextIds[i] = ids.get(i);
            intent.putExtra("context_ids", contextIds);
            intent.putExtra("context_index", index);
        }
        startActivity(intent);
    }

    private void showEditSubjectItemDialog(SubjectItem item) {
        AddSubjectItemDialog dialog = AddSubjectItemDialog.newInstance(subject.getId(), item);
        dialog.setOnSubjectItemSavedListener(value -> loadSubject(subject.getId()));
        dialog.show(getParentFragmentManager(), "EditSubjectItemDialog");
    }

    private void setupDragAndDrop() {
        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
                int start = from.getAdapterPosition(), end = to.getAdapterPosition();
                List<SubjectItem> items = adapter.getItems();
                if (start < 0 || end < 0 || start >= items.size() || end >= items.size()) return false;
                SubjectItem moved = items.remove(start);
                items.add(end, moved);
                adapter.notifyItemMoved(start, end);
                return true;
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) { }
            @Override public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder holder) {
                super.clearView(rv, holder);
                List<SubjectItem> items = adapter.getItems();
                for (int i = 0; i < items.size(); i++) {
                    items.get(i).setOrderIndex(i * SubjectUtil.ORDER_INTERVAL);
                    subjectDao.updateSubjectItem(items.get(i));
                }
            }
        };
        new ItemTouchHelper(callback).attachToRecyclerView(recyclerView);
    }

    @Override public void onCollectLink(SubjectItem item, LinkItem link) {
        for (LinkItem existing : linkDao.getAllLinks()) if (link.getUrl() != null && link.getUrl().equals(existing.getUrl())) {
            Toast.makeText(requireContext(), "链接已存在于主页", Toast.LENGTH_SHORT).show();
            return;
        }
        linkDao.insertLink(link);
        Toast.makeText(requireContext(), "链接已收录到主页", Toast.LENGTH_SHORT).show();
    }

    @Override public void onDeleteSubjectItem(SubjectItem item) { if (subjectDao.deleteSubjectItem(item.getId())) loadSubject(subject.getId()); }
    @Override public void onArchiveSubjectItem(SubjectItem item) { if (subjectDao.archiveSubjectItem(item.getId(), System.currentTimeMillis())) loadSubject(subject.getId()); }
    @Override public void onRestoreSubjectItem(SubjectItem item) { if (subjectDao.restoreSubjectItem(item.getId())) loadSubject(subject.getId()); }
    @Override public void onRefreshItems() { if (subject != null) loadSubject(subject.getId()); }

    private void showSubjectSettingsDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_subject_settings, null);
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setTitle("设置").setView(view).setNegativeButton("关闭", null).create();
        view.findViewById(R.id.btn_archived).setOnClickListener(v -> {
            dialog.dismiss();
            if (subject != null) {
                SubjectArchivedItemsDialog archived = SubjectArchivedItemsDialog.newInstance(subject.getId());
                archived.setOnItemsRestoredListener(() -> loadSubject(subject.getId()));
                archived.show(getParentFragmentManager(), "SubjectArchivedItemsDialog");
            }
        });
        dialog.show();
    }
}
