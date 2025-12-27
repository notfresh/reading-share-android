package person.notfresh.readingshare.ui.subject;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.adapter.SubjectAdapter;
import person.notfresh.readingshare.core.model.Subject;
import person.notfresh.readingshare.db.SubjectDao;

/**
 * 选择主题对话框（用于从主页/标签页添加链接到主题）
 */
public class SelectSubjectDialog extends DialogFragment implements SubjectAdapter.OnSubjectClickListener {
    private static final String TAG = "SelectSubjectDialog";

    private RecyclerView recyclerView;
    private SubjectAdapter adapter;
    private SubjectDao subjectDao;
    private OnSubjectSelectedListener listener;
    private List<Long> linkIds; // 要添加的链接ID列表

    public interface OnSubjectSelectedListener {
        void onSubjectSelected(long subjectId, List<Long> linkIds);
    }

    /**
     * 创建选择主题对话框
     */
    public static SelectSubjectDialog newInstance(List<Long> linkIds) {
        SelectSubjectDialog dialog = new SelectSubjectDialog();
        dialog.linkIds = linkIds;
        return dialog;
    }

    public void setOnSubjectSelectedListener(OnSubjectSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        subjectDao = new SubjectDao(requireContext());
        subjectDao.open();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (subjectDao != null) {
            subjectDao.close();
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_select_subject, null);

        recyclerView = view.findViewById(R.id.recycler_view);
        adapter = new SubjectAdapter(requireContext());
        adapter.setOnSubjectClickListener(this);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        // 加载所有主题
        List<Subject> subjects = subjectDao.getAllSubjects();
        adapter.setSubjects(subjects);

        if (subjects.isEmpty()) {
            Toast.makeText(requireContext(), "请先创建主题", Toast.LENGTH_SHORT).show();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle("选择主题")
                .setView(view)
                .setNegativeButton("取消", null);

        return builder.create();
    }

    @Override
    public void onSubjectClick(Subject subject) {
        if (listener != null && linkIds != null) {
            listener.onSubjectSelected(subject.getId(), linkIds);
        }
        dismiss();
    }
}

