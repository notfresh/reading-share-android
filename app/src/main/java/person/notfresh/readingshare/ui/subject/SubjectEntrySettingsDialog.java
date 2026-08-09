package person.notfresh.readingshare.ui.subject;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.core.model.Subject;
import person.notfresh.readingshare.core.storage.KeyValueStorage;
import person.notfresh.readingshare.db.SubjectDao;
import person.notfresh.readingshare.util.android.SharedPreferencesStorage;

/**
 * 主题入口设置对话框
 */
public class SubjectEntrySettingsDialog extends Dialog {

    private SubjectEntryManager manager;
    private SubjectDao subjectDao;

    private RadioGroup radioGroupPreference;
    private RadioButton radioDetail;
    private RadioButton radioList;
    private Spinner spinnerMemorySubject;
    private TextView textMemoryHint;

    private List<Subject> subjects = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter;
    private List<String> subjectNames = new ArrayList<>();

    public interface OnSettingsSavedListener {
        void onSettingsSaved();
    }

    private OnSettingsSavedListener listener;

    public SubjectEntrySettingsDialog(@NonNull Context context) {
        super(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_subject_entry_settings);

        // 初始化存储和Manager
        KeyValueStorage storage = new SharedPreferencesStorage(getContext(), "subject_entry_prefs");
        manager = new SubjectEntryManager(storage);

        // 初始化数据库
        subjectDao = new SubjectDao(getContext());
        subjectDao.open();

        initViews();
        loadSettings();
        loadSubjects();
    }

    private void initViews() {
        radioGroupPreference = findViewById(R.id.radio_group_entry_preference);
        radioDetail = findViewById(R.id.radio_detail);
        radioList = findViewById(R.id.radio_list);
        spinnerMemorySubject = findViewById(R.id.spinner_memory_subject);
        textMemoryHint = findViewById(R.id.text_memory_hint);

        // 设置入口偏好RadioGroup监听
        radioGroupPreference.setOnCheckedChangeListener((group, checkedId) -> {
            // 如果选择列表，隐藏记忆主题选项
            if (checkedId == R.id.radio_list) {
                spinnerMemorySubject.setVisibility(View.GONE);
                textMemoryHint.setVisibility(View.GONE);
            } else {
                spinnerMemorySubject.setVisibility(View.VISIBLE);
                textMemoryHint.setVisibility(View.VISIBLE);
            }
        });

        // 初始化下拉选择器
        spinnerAdapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, subjectNames);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMemorySubject.setAdapter(spinnerAdapter);

        // 设置确定和取消按钮
        Button btnSave = findViewById(R.id.btn_save);
        Button btnCancel = findViewById(R.id.btn_cancel);

        btnSave.setOnClickListener(v -> saveSettings());
        btnCancel.setOnClickListener(v -> dismiss());
    }

    private void loadSettings() {
        // 加载入口偏好
        SubjectEntryManager.EntryPreference preference = manager.getEntryPreference();
        if (preference == SubjectEntryManager.EntryPreference.LIST) {
            radioList.setChecked(true);
        } else {
            radioDetail.setChecked(true);
        }
    }

    private void loadSubjects() {
        subjects = subjectDao.getAllSubjects();

        subjectNames.clear();
        subjectNames.add("上次查看");
        for (Subject subject : subjects) {
            subjectNames.add(subject.getTitle());
        }
        spinnerAdapter.notifyDataSetChanged();

        // 加载记忆的主题选择
        long memorySubjectId = manager.getMemorySubjectId();
        if (memorySubjectId == SubjectEntryManager.VALUE_LAST_VIEWED) {
            spinnerMemorySubject.setSelection(0);
        } else {
            for (int i = 0; i < subjects.size(); i++) {
                if (subjects.get(i).getId() == memorySubjectId) {
                    spinnerMemorySubject.setSelection(i + 1);
                    break;
                }
            }
        }
    }

    private void saveSettings() {
        // 保存入口偏好
        SubjectEntryManager.EntryPreference preference;
        if (radioList.isChecked()) {
            preference = SubjectEntryManager.EntryPreference.LIST;
        } else {
            preference = SubjectEntryManager.EntryPreference.DETAIL;
        }
        manager.setEntryPreference(preference);

        // 保存记忆的主题
        int selectedPosition = spinnerMemorySubject.getSelectedItemPosition();
        if (selectedPosition == 0) {
            manager.setMemorySubjectId(SubjectEntryManager.VALUE_LAST_VIEWED);
        } else if (selectedPosition > 0 && selectedPosition <= subjects.size()) {
            manager.setMemorySubjectId(subjects.get(selectedPosition - 1).getId());
        }

        if (listener != null) {
            listener.onSettingsSaved();
        }
        dismiss();
    }

    public void setOnSettingsSavedListener(OnSettingsSavedListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (subjectDao != null) {
            subjectDao.close();
        }
    }
}