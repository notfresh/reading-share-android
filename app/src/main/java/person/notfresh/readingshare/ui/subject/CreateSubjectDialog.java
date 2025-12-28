package person.notfresh.readingshare.ui.subject;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.textfield.TextInputEditText;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.core.model.Subject;

/**
 * 创建/编辑主题对话框
 */
public class CreateSubjectDialog extends DialogFragment {
    private static final String ARG_SUBJECT = "subject";
    private static final String ARG_IS_EDIT = "is_edit";

    private TextInputEditText editTitle;
    private TextInputEditText editDescribe;
    private OnSubjectSavedListener listener;
    private Subject subject; // 编辑模式时的主题对象

    public interface OnSubjectSavedListener {
        void onSubjectSaved(Subject subject);
    }

    /**
     * 创建新主题对话框
     */
    public static CreateSubjectDialog newInstance() {
        CreateSubjectDialog dialog = new CreateSubjectDialog();
        Bundle args = new Bundle();
        args.putBoolean(ARG_IS_EDIT, false);
        dialog.setArguments(args);
        return dialog;
    }

    /**
     * 编辑主题对话框
     */
    public static CreateSubjectDialog newInstance(Subject subject) {
        CreateSubjectDialog dialog = new CreateSubjectDialog();
        Bundle args = new Bundle();
        args.putBoolean(ARG_IS_EDIT, true);
        // 通过静态变量传递，因为Subject没有实现Serializable
        dialog.subject = subject;
        dialog.setArguments(args);
        return dialog;
    }

    public void setOnSubjectSavedListener(OnSubjectSavedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        boolean isEdit = getArguments() != null && getArguments().getBoolean(ARG_IS_EDIT, false);
        // subject 已经通过静态变量在 newInstance 中设置了，不需要从 Bundle 获取

        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_create_subject, null);

        editTitle = view.findViewById(R.id.edit_title);
        editDescribe = view.findViewById(R.id.edit_describe);

        // 如果是编辑模式，预填充数据
        if (isEdit && subject != null) {
            editTitle.setText(subject.getTitle() != null ? subject.getTitle() : "");
            editDescribe.setText(subject.getDescribe() != null ? subject.getDescribe() : "");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle(isEdit ? "编辑主题" : "创建主题")
                .setView(view)
                .setPositiveButton("保存", null) // 先设置为null，后面自定义处理
                .setNegativeButton("取消", null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (validateAndSave()) {
                    dialog.dismiss();
                }
            });
        });

        return dialog;
    }

    private boolean validateAndSave() {
        String title = editTitle.getText() != null ? editTitle.getText().toString().trim() : "";
        String describe = editDescribe.getText() != null ? editDescribe.getText().toString().trim() : "";

        if (TextUtils.isEmpty(title)) {
            Toast.makeText(requireContext(), "请输入主题标题", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (subject == null) {
            // 创建新主题
            subject = new Subject(title, describe);
        } else {
            // 更新现有主题
            subject.setTitle(title);
            subject.setDescribe(describe);
        }

        if (listener != null) {
            listener.onSubjectSaved(subject);
        }

        return true;
    }
}

