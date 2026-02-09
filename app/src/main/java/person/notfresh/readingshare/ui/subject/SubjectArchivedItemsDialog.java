package person.notfresh.readingshare.ui.subject;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.adapter.SubjectItemAdapter;
import person.notfresh.readingshare.core.model.SubjectItem;
import person.notfresh.readingshare.db.SubjectDao;
import person.notfresh.readingshare.model.LinkItem;

/**
 * 已归档主题项列表对话框
 */
public class SubjectArchivedItemsDialog extends DialogFragment implements SubjectItemAdapter.OnSubjectItemActionListener {
    private static final String ARG_SUBJECT_ID = "subject_id";

    private RecyclerView recyclerView;
    private TextView textSortToggle;
    private TextView textEmpty;
    private SubjectItemAdapter adapter;
    private SubjectDao subjectDao;
    private long subjectId;
    private boolean isAscending = false;
    private OnItemsRestoredListener restoredListener;

    public interface OnItemsRestoredListener {
        void onItemsRestored();
    }

    public static SubjectArchivedItemsDialog newInstance(long subjectId) {
        SubjectArchivedItemsDialog dialog = new SubjectArchivedItemsDialog();
        Bundle args = new Bundle();
        args.putLong(ARG_SUBJECT_ID, subjectId);
        dialog.setArguments(args);
        return dialog;
    }

    public void setOnItemsRestoredListener(OnItemsRestoredListener listener) {
        this.restoredListener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            subjectId = getArguments().getLong(ARG_SUBJECT_ID, -1);
        }
        subjectDao = new SubjectDao(requireContext());
        subjectDao.open();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (adapter != null) {
            adapter.close();
        }
        if (subjectDao != null) {
            subjectDao.close();
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        if (subjectId == -1) {
            Toast.makeText(requireContext(), "主题ID无效", Toast.LENGTH_SHORT).show();
            return new AlertDialog.Builder(requireContext())
                    .setTitle("已归档")
                    .setMessage("主题ID无效")
                    .setNegativeButton("关闭", null)
                    .create();
        }
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_subject_archived_items, null);

        recyclerView = view.findViewById(R.id.recycler_archived_items);
        textSortToggle = view.findViewById(R.id.text_sort_toggle);
        textEmpty = view.findViewById(R.id.text_empty);

        adapter = new SubjectItemAdapter(requireContext());
        adapter.setArchiveMode(true);
        adapter.setOnSubjectItemActionListener(this);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        textSortToggle.setOnClickListener(v -> {
            isAscending = !isAscending;
            updateSortLabel();
            loadArchivedItems();
        });
        updateSortLabel();
        loadArchivedItems();

        return new AlertDialog.Builder(requireContext())
                .setTitle("已归档")
                .setView(view)
                .setNegativeButton("关闭", null)
                .create();
    }

    private void updateSortLabel() {
        textSortToggle.setText(isAscending ? "排序：归档时间正序" : "排序：归档时间倒序");
    }

    private void loadArchivedItems() {
        List<SubjectItem> items = subjectDao.getArchivedSubjectItemsBySubjectId(subjectId, isAscending);
        adapter.setItems(items);
        updateEmptyState(items);
    }

    private void updateEmptyState(List<SubjectItem> items) {
        if (items == null || items.isEmpty()) {
            textEmpty.setVisibility(View.VISIBLE);
        } else {
            textEmpty.setVisibility(View.GONE);
        }
    }

    @Override
    public void onCollectLink(SubjectItem item, LinkItem linkItem) {
        Toast.makeText(requireContext(), "请在主题详情页操作收录", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDeleteSubjectItem(SubjectItem item) {
        boolean deleted = subjectDao.deleteSubjectItem(item.getId());
        if (deleted) {
            adapter.removeItem(item);
            updateEmptyState(adapter.getItems());
            Toast.makeText(requireContext(), "主题项已删除", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), "删除失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onArchiveSubjectItem(SubjectItem item) {
        // 归档列表不需要再次归档
    }

    @Override
    public void onRestoreSubjectItem(SubjectItem item) {
        boolean restored = subjectDao.restoreSubjectItem(item.getId());
        if (restored) {
            adapter.removeItem(item);
            updateEmptyState(adapter.getItems());
            Toast.makeText(requireContext(), "已还原", Toast.LENGTH_SHORT).show();
            if (restoredListener != null) {
                restoredListener.onItemsRestored();
            }
        } else {
            Toast.makeText(requireContext(), "还原失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRefreshItems() {
        loadArchivedItems();
    }
}
