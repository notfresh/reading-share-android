package person.notfresh.readingshare.adapter;

import android.app.AlertDialog;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.core.model.Subject;
import person.notfresh.readingshare.util.ShortcutUtil;

/**
 * 主题列表适配器
 */
public class SubjectAdapter extends RecyclerView.Adapter<SubjectAdapter.SubjectViewHolder> {
    private List<Subject> subjects = new ArrayList<>();
    private OnSubjectClickListener listener;
    private OnSubjectActionListener actionListener;
    private Context context;
    private boolean sortMode = false;

    public interface OnSubjectClickListener {
        void onSubjectClick(Subject subject);
    }

    public interface OnSubjectActionListener {
        void onEditSubject(Subject subject);
        void onDeleteSubject(Subject subject);
        void onAddToDesktop(Subject subject);
        /**
         * 请求选择自定义图标（从相册选择图片）
         * @param subject 主题
         */
        void onRequestCustomIcon(Subject subject);
    }

    public SubjectAdapter(Context context) {
        this.context = context;
    }

    public void setOnSubjectClickListener(OnSubjectClickListener listener) {
        this.listener = listener;
    }

    public void setOnSubjectActionListener(OnSubjectActionListener listener) {
        this.actionListener = listener;
    }

    public void setSortMode(boolean sortMode) {
        this.sortMode = sortMode;
    }

    public void setSubjects(List<Subject> subjects) {
        this.subjects = subjects != null ? new ArrayList<>(subjects) : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SubjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_subject, parent, false);
        return new SubjectViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SubjectViewHolder holder, int position) {
        Subject subject = subjects.get(position);
        holder.bind(subject);
    }

    @Override
    public int getItemCount() {
        return subjects.size();
    }

    /**
     * 获取当前主题列表（用于拖拽排序）
     */
    public List<Subject> getSubjects() {
        return subjects;
    }

    class SubjectViewHolder extends RecyclerView.ViewHolder {
        private TextView textTitle;
        private TextView textDescribe;

        SubjectViewHolder(@NonNull View itemView) {
            super(itemView);
            textTitle = itemView.findViewById(R.id.text_title);
            textDescribe = itemView.findViewById(R.id.text_describe);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onSubjectClick(subjects.get(position));
                }
            });

            // 长按显示操作菜单（排序模式下禁用）
            itemView.setOnLongClickListener(v -> {
                if (sortMode) {
                    // 排序模式下不处理长按，交给 ItemTouchHelper 处理拖拽
                    return false;
                }
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    showActionMenu(v, subjects.get(position));
                }
                return true;
            });
        }

        void bind(Subject subject) {
            textTitle.setText(subject.getTitle() != null ? subject.getTitle() : "");
            textDescribe.setText(subject.getDescribe() != null ? subject.getDescribe() : "");
        }

        /**
         * 显示操作菜单（长按）
         */
        private void showActionMenu(View view, Subject subject) {
            Context wrapper = new ContextThemeWrapper(context, R.style.PopupMenuTheme);
            PopupMenu popup = new PopupMenu(wrapper, view);
            popup.getMenu().add(0, 1, 0, "编辑");
            popup.getMenu().add(0, 2, 0, "删除");
            popup.getMenu().add(0, 3, 0, "添加到桌面");

            popup.setOnMenuItemClickListener(menuItem -> {
                if (actionListener == null) {
                    return false;
                }
                switch (menuItem.getItemId()) {
                    case 1: // 编辑
                        actionListener.onEditSubject(subject);
                        return true;
                    case 2: // 删除
                        new AlertDialog.Builder(context)
                                .setTitle("确认删除")
                                .setMessage("确定要删除主题 \"" + subject.getTitle() + "\" 吗？\n\n删除后无法恢复，所有主题项和图片将被删除。")
                                .setPositiveButton("删除", (dialog, which) -> {
                                    actionListener.onDeleteSubject(subject);
                                })
                                .setNegativeButton("取消", null)
                                .show();
                        return true;
                    case 3: // 添加到桌面
                        showIconSelectionDialog(view, subject);
                        return true;
                    default:
                        return false;
                }
            });

            popup.show();
        }

        /**
         * 显示图标选择对话框
         */
        private void showIconSelectionDialog(View view, Subject subject) {
            String[] options = {"从相册选择", "使用默认图标"};
            
            new AlertDialog.Builder(context)
                    .setTitle("选择快捷方式图标")
                    .setItems(options, (dialog, which) -> {
                        switch (which) {
                            case 0:
                                // 从相册选择
                                if (actionListener != null) {
                                    actionListener.onRequestCustomIcon(subject);
                                } else {
                                    Toast.makeText(context, "无法打开相册", Toast.LENGTH_SHORT).show();
                                }
                                break;
                            case 1:
                                // 使用默认图标
                                createShortcutWithDefaultIcon(view, subject);
                                break;
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        }

        /**
         * 使用默认图标创建快捷方式
         */
        private void createShortcutWithDefaultIcon(View view, Subject subject) {
            String title = subject.getTitle() != null ? subject.getTitle() : "主题";
            boolean success = ShortcutUtil.createSubjectShortcut(
                context, 
                title, 
                subject.getId(),
                null  // 使用默认图标
            );
            if (success) {
                Toast.makeText(context, "已添加快捷方式", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "创建快捷方式失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 使用自定义图标创建快捷方式（由 Fragment 调用）
     */
    public void createShortcutWithCustomIcon(Context context, Subject subject, android.graphics.Bitmap customIcon) {
        String title = subject.getTitle() != null ? subject.getTitle() : "主题";
        boolean success = ShortcutUtil.createSubjectShortcut(
            context, 
            title, 
            subject.getId(),
            customIcon
        );
        if (success) {
            Toast.makeText(context, "已添加快捷方式", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "创建快捷方式失败", Toast.LENGTH_SHORT).show();
        }
    }
}

