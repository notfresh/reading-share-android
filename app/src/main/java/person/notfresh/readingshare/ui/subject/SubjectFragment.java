package person.notfresh.readingshare.ui.subject;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import android.content.Intent;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.adapter.SubjectAdapter;
import person.notfresh.readingshare.core.model.Subject;
import person.notfresh.readingshare.db.SubjectDao;
import person.notfresh.readingshare.ui.subject.CreateSubjectDialog;
import person.notfresh.readingshare.ui.subject.SubjectDetailActivity;
import person.notfresh.readingshare.util.ShortcutUtil;

/**
 * 主题列表页Fragment
 */
public class SubjectFragment extends Fragment implements SubjectAdapter.OnSubjectClickListener {
    private static final String TAG = "SubjectFragment";

    private RecyclerView recyclerView;
    private SubjectAdapter adapter;
    private SubjectDao subjectDao;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.subject_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_create_subject) {
            showCreateSubjectDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showCreateSubjectDialog() {
        CreateSubjectDialog dialog = CreateSubjectDialog.newInstance();
        dialog.setOnSubjectSavedListener(subject -> {
            // 保存主题到数据库
            subjectDao.insertSubject(subject);
            // 刷新列表
            loadSubjects();
            Toast.makeText(requireContext(), "主题创建成功", Toast.LENGTH_SHORT).show();
        });
        dialog.show(getParentFragmentManager(), "CreateSubjectDialog");
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                            ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_subject, container, false);

        subjectDao = new SubjectDao(requireContext());
        subjectDao.open();

        recyclerView = root.findViewById(R.id.recycler_view);
        adapter = new SubjectAdapter(requireContext());
        adapter.setOnSubjectClickListener(this);
        adapter.setOnSubjectActionListener(new SubjectAdapter.OnSubjectActionListener() {
            @Override
            public void onEditSubject(Subject subject) {
                showEditSubjectDialog(subject);
            }

            @Override
            public void onDeleteSubject(Subject subject) {
                // 删除主题（级联删除所有主题项和图片）
                boolean deleted = subjectDao.deleteSubject(subject.getId());
                if (deleted) {
                    loadSubjects();
                    Toast.makeText(requireContext(), "主题已删除", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "删除失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onAddToDesktop(Subject subject) {
                // 创建主题快捷方式
                createSubjectShortcut(subject);
            }
        });
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        loadSubjects();

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (subjectDao != null) {
            subjectDao.close();
        }
    }

    private void loadSubjects() {
        List<Subject> subjects = subjectDao.getAllSubjects();
        adapter.setSubjects(subjects);
        Log.d(TAG, "加载了 " + subjects.size() + " 个主题");
    }

    @Override
    public void onSubjectClick(Subject subject) {
        Intent intent = new Intent(requireContext(), SubjectDetailActivity.class);
        intent.putExtra(SubjectDetailActivity.EXTRA_SUBJECT_ID, subject.getId());
        startActivity(intent);
    }

    private void showEditSubjectDialog(Subject subject) {
        CreateSubjectDialog dialog = CreateSubjectDialog.newInstance(subject);
        dialog.setOnSubjectSavedListener(editedSubject -> {
            // 更新主题
            subjectDao.updateSubject(editedSubject);
            // 刷新列表
            loadSubjects();
            Toast.makeText(requireContext(), "主题更新成功", Toast.LENGTH_SHORT).show();
        });
        dialog.show(getParentFragmentManager(), "EditSubjectDialog");
    }

    /**
     * 创建主题桌面快捷方式
     */
    private void createSubjectShortcut(Subject subject) {
        String title = subject.getTitle() != null ? subject.getTitle() : "主题";
        ShortcutUtil.createSubjectShortcut(requireContext(), title, subject.getId());
    }
}

