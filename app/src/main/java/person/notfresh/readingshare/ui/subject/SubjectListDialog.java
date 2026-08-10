package person.notfresh.readingshare.ui.subject;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.adapter.SubjectAdapter;
import person.notfresh.readingshare.core.model.Subject;
import person.notfresh.readingshare.core.model.SubjectUtil;
import person.notfresh.readingshare.db.SubjectDao;
import person.notfresh.readingshare.util.ImageUtil;

/** 主题切换与主题管理弹窗。 */
public class SubjectListDialog extends DialogFragment implements SubjectAdapter.OnSubjectClickListener {
    private static final int REQUEST_CODE_PICK_ICON = 1001;

    private RecyclerView recyclerView;
    private SubjectAdapter adapter;
    private SubjectDao subjectDao;
    private Subject selectedSubject;
    private boolean sortMode;
    private OnSubjectSelectedListener selectedListener;
    private OnSubjectDeletedListener deletedListener;
    private OnDismissListener dismissListener;

    public interface OnSubjectSelectedListener {
        void onSubjectSelected(long subjectId);
    }

    public interface OnSubjectDeletedListener {
        void onSubjectDeleted(long subjectId);
    }

    public interface OnDismissListener {
        void onDismiss();
    }

    public static SubjectListDialog newInstance(long selectedSubjectId) {
        SubjectListDialog dialog = new SubjectListDialog();
        Bundle args = new Bundle();
        args.putLong("selected_subject_id", selectedSubjectId);
        dialog.setArguments(args);
        return dialog;
    }

    public void setOnSubjectSelectedListener(OnSubjectSelectedListener listener) {
        selectedListener = listener;
    }

    public void setOnSubjectDeletedListener(OnSubjectDeletedListener listener) {
        deletedListener = listener;
    }

    public void setOnDismissListener(OnDismissListener listener) {
        dismissListener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        subjectDao = new SubjectDao(requireContext());
        subjectDao.open();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_select_subject, null);
        recyclerView = view.findViewById(R.id.recycler_view);
        Button createButton = view.findViewById(R.id.button_create_subject);
        Button sortButton = view.findViewById(R.id.button_sort_subjects);

        adapter = new SubjectAdapter(requireContext());
        adapter.setOnSubjectClickListener(this);
        adapter.setOnSubjectActionListener(new SubjectAdapter.OnSubjectActionListener() {
            @Override
            public void onEditSubject(Subject subject) {
                showEditSubjectDialog(subject);
            }

            @Override
            public void onDeleteSubject(Subject subject) {
                if (subjectDao.deleteSubject(subject.getId())) {
                    loadSubjects();
                    if (deletedListener != null) {
                        deletedListener.onSubjectDeleted(subject.getId());
                    }
                    Toast.makeText(requireContext(), "主题已删除", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onAddToDesktop(Subject subject) {
                createSubjectShortcut(subject, null);
            }

            @Override
            public void onRequestCustomIcon(Subject subject) {
                selectedSubject = subject;
                startActivityForResult(ImageUtil.createGalleryPickerIntent(), REQUEST_CODE_PICK_ICON);
            }
        });
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        setupDragAndDrop();
        loadSubjects();

        createButton.setOnClickListener(v -> showCreateSubjectDialog());
        sortButton.setOnClickListener(v -> {
            sortMode = !sortMode;
            adapter.setSortMode(sortMode);
            sortButton.setText(sortMode ? "完成排序" : "排序主题");
        });

        return new AlertDialog.Builder(requireContext())
                .setTitle("切换主题")
                .setView(view)
                .setNegativeButton("取消", null)
                .create();
    }

    @Override
    public void onDestroy() {
        if (adapter != null) {
            adapter.setOnSubjectClickListener(null);
        }
        if (subjectDao != null) {
            subjectDao.close();
        }
        super.onDestroy();
    }

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        super.onDismiss(dialog);
        if (dismissListener != null) {
            dismissListener.onDismiss();
        }
    }

    @Override
    public void onSubjectClick(Subject subject) {
        if (selectedListener != null) {
            selectedListener.onSubjectSelected(subject.getId());
        }
        dismiss();
    }

    private void loadSubjects() {
        if (adapter != null) {
            adapter.setSubjects(subjectDao.getAllSubjects());
        }
    }

    private void showCreateSubjectDialog() {
        CreateSubjectDialog dialog = CreateSubjectDialog.newInstance();
        dialog.setOnSubjectSavedListener(subject -> {
            subjectDao.insertSubject(subject);
            loadSubjects();
            Toast.makeText(requireContext(), "主题创建成功", Toast.LENGTH_SHORT).show();
        });
        dialog.show(getParentFragmentManager(), "CreateSubjectDialog");
    }

    private void showEditSubjectDialog(Subject subject) {
        CreateSubjectDialog dialog = CreateSubjectDialog.newInstance(subject);
        dialog.setOnSubjectSavedListener(editedSubject -> {
            subjectDao.updateSubject(editedSubject);
            loadSubjects();
            Toast.makeText(requireContext(), "主题更新成功", Toast.LENGTH_SHORT).show();
        });
        dialog.show(getParentFragmentManager(), "EditSubjectDialog");
    }

    private void setupDragAndDrop() {
        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();
                List<Subject> subjects = adapter.getSubjects();
                if (from < 0 || to < 0 || from >= subjects.size() || to >= subjects.size()) {
                    return false;
                }
                Subject subject = subjects.remove(from);
                subjects.add(to, subject);
                adapter.notifyItemMoved(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                List<Subject> subjects = adapter.getSubjects();
                for (int i = 0; i < subjects.size(); i++) {
                    subjects.get(i).setOrderIndex(i * SubjectUtil.ORDER_INTERVAL);
                }
                subjectDao.updateSubjectsOrderIndex(subjects);
            }
        };
        new ItemTouchHelper(callback).attachToRecyclerView(recyclerView);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CODE_PICK_ICON || resultCode != android.app.Activity.RESULT_OK
                || data == null || data.getData() == null || selectedSubject == null) {
            selectedSubject = null;
            return;
        }
        try {
            Uri uri = data.getData();
            Bitmap bitmap = ImageUtil.uriToBitmap(requireContext(), uri);
            if (bitmap != null) {
                Bitmap squareBitmap = ImageUtil.resizeToSquareForShortcut(bitmap);
                if (squareBitmap != bitmap) {
                    bitmap.recycle();
                }
                adapter.createShortcutWithCustomIcon(requireContext(), selectedSubject, squareBitmap);
            }
        } finally {
            selectedSubject = null;
        }
    }

    private void createSubjectShortcut(Subject subject, Bitmap customIcon) {
        if (customIcon == null) {
            person.notfresh.readingshare.util.ShortcutUtil.createSubjectShortcut(
                    requireContext(), subject.getTitle(), subject.getId(), null);
        }
    }
}