package person.notfresh.readingshare.ui.subject;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import androidx.appcompat.widget.Toolbar;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.adapter.SubjectAdapter;
import person.notfresh.readingshare.core.model.Subject;
import person.notfresh.readingshare.core.model.SubjectUtil;
import person.notfresh.readingshare.db.SubjectDao;
import person.notfresh.readingshare.ui.subject.CreateSubjectDialog;
import person.notfresh.readingshare.ui.subject.SubjectDetailActivity;
import person.notfresh.readingshare.util.ImageUtil;
import person.notfresh.readingshare.util.ShortcutUtil;
import person.notfresh.readingshare.core.storage.KeyValueStorage;
import person.notfresh.readingshare.util.android.SharedPreferencesStorage;

/**
 * 主题列表页Fragment
 */
public class SubjectFragment extends Fragment implements SubjectAdapter.OnSubjectClickListener {
    private static final String TAG = "SubjectFragment";
    private static final int REQUEST_CODE_PICK_ICON = 1001;

    private RecyclerView recyclerView;
    private SubjectAdapter adapter;
    private SubjectDao subjectDao;
    private Subject pendingIconSubject; // 临时存储待处理图标的主题
    private boolean isSortMode = false;
    private ItemTouchHelper itemTouchHelper;
    private MenuItem sortMenuItem;
    private SubjectEntryManager subjectEntryManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // 初始化 SubjectEntryManager
        KeyValueStorage storage = new SharedPreferencesStorage(requireContext(), "subject_entry_prefs");
        subjectEntryManager = new SubjectEntryManager(storage);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.subject_menu, menu);
        sortMenuItem = menu.findItem(R.id.action_sort_subjects);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_create_subject) {
            showCreateSubjectDialog();
            return true;
        } else if (item.getItemId() == R.id.action_sort_subjects) {
            toggleSortMode();
            return true;
        } else if (item.getItemId() == R.id.action_subject_entry_settings) {
            showEntrySettingsDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toggleSortMode() {
        isSortMode = !isSortMode;
        adapter.setSortMode(isSortMode);

        if (isSortMode) {
            // 进入排序模式
            sortMenuItem.setTitle("完成");
            Toolbar toolbar = requireActivity().findViewById(R.id.toolbar);
            if (toolbar != null) {
                toolbar.setTitle("排序中...");
            }
            itemTouchHelper.attachToRecyclerView(recyclerView);
        } else {
            // 退出排序模式
            sortMenuItem.setTitle("排序");
            Toolbar toolbar = requireActivity().findViewById(R.id.toolbar);
            if (toolbar != null) {
                toolbar.setTitle(getString(R.string.menu_subject));
            }
            itemTouchHelper.attachToRecyclerView(null);
        }
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

    private void showEntrySettingsDialog() {
        SubjectEntrySettingsDialog dialog = new SubjectEntrySettingsDialog(requireContext());
        dialog.setOnSettingsSavedListener(() -> {
            // 设置保存后，重新检查入口逻辑
            checkAndNavigateToEntry();
        });
        dialog.show();
    }

    private void checkAndNavigateToEntry() {
        Long targetSubjectId = subjectEntryManager.getDefaultEntryTarget();
        if (targetSubjectId != null && targetSubjectId > 0) {
            // 跳转到主题详情
            Intent intent = new Intent(requireContext(), SubjectDetailActivity.class);
            intent.putExtra(SubjectDetailActivity.EXTRA_SUBJECT_ID, targetSubjectId);
            startActivity(intent);
        }
        // 否则显示主题列表（默认行为）
    }

    @Override
    public void onResume() {
        super.onResume();
        checkAndNavigateToEntry();
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
                // 这个方法现在不会被直接调用，因为 SubjectAdapter 会显示图标选择对话框
                // 保留此方法以保持接口兼容性
                createSubjectShortcut(subject);
            }

            @Override
            public void onRequestCustomIcon(Subject subject) {
                Log.d(TAG, "onRequestCustomIcon: " + subject.getTitle());
                
                // 保存临时数据
                pendingIconSubject = subject;
                
                // 打开相册选择图片
                Intent intent = ImageUtil.createGalleryPickerIntent();
                startActivityForResult(intent, REQUEST_CODE_PICK_ICON);
            }
        });
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        setupDragAndDrop();

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

    private void setupDragAndDrop() {
        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                 @NonNull RecyclerView.ViewHolder viewHolder,
                                 @NonNull RecyclerView.ViewHolder target) {
                int fromPos = viewHolder.getAdapterPosition();
                int toPos = target.getAdapterPosition();

                List<Subject> items = adapter.getSubjects();
                if (fromPos < 0 || fromPos >= items.size() || toPos < 0 || toPos >= items.size()) {
                    return false;
                }

                Subject draggedItem = items.get(fromPos);
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

                // 拖拽结束，按当前顺序重新分配 orderIndex 并保存
                List<Subject> items = adapter.getSubjects();
                for (int i = 0; i < items.size(); i++) {
                    items.get(i).setOrderIndex(i * SubjectUtil.ORDER_INTERVAL);
                }

                subjectDao.updateSubjectsOrderIndex(items);
                Log.d(TAG, "拖拽排序完成，已更新 " + items.size() + " 个主题的 orderIndex");
            }
        };

        itemTouchHelper = new ItemTouchHelper(callback);
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_CODE_PICK_ICON && resultCode == android.app.Activity.RESULT_OK) {
            if (data != null && data.getData() != null && pendingIconSubject != null) {
                Uri selectedImageUri = data.getData();
                Log.d(TAG, "图片选择成功: " + selectedImageUri);
                
                try {
                    // 1. 将 URI 转换为 Bitmap
                    Bitmap bitmap = ImageUtil.uriToBitmap(requireContext(), selectedImageUri);
                    
                    if (bitmap != null) {
                        // 2. 缩放为正方形（快捷方式图标需要 256x256）
                        Bitmap squareBitmap = ImageUtil.resizeToSquareForShortcut(bitmap);
                        
                        // 3. 释放原图内存
                        if (squareBitmap != bitmap) {
                            bitmap.recycle();
                        }
                        
                        // 4. 创建快捷方式
                        adapter.createShortcutWithCustomIcon(
                            requireContext(), 
                            pendingIconSubject, 
                            squareBitmap
                        );
                        
                        Log.d(TAG, "快捷方式创建成功（使用自定义图标）");
                    } else {
                        Toast.makeText(requireContext(), "无法读取图片", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "无法从 URI 读取图片");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "处理图片时出错: " + e.getMessage(), e);
                    Toast.makeText(requireContext(), "处理图片时出错: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                } finally {
                    // 清理临时数据
                    pendingIconSubject = null;
                }
            } else {
                Log.w(TAG, "图片选择失败：data 或 pendingIconSubject 为 null");
                pendingIconSubject = null;
            }
        }
    }
}

