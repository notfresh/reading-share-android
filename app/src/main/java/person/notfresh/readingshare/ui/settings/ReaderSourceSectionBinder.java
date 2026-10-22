package person.notfresh.readingshare.ui.settings;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.core.model.Subject;
import person.notfresh.readingshare.db.DbConnection;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.db.SubjectDao;
import person.notfresh.readingshare.model.ReaderSourceSpec;
import person.notfresh.readingshare.util.ReaderSourceStore;

/**
 * 设置页「阅读页数据来源」区块（2.3.19）。
 *
 * <p>为什么独立成类：SettingFragment 已经 970 行，再塞 150 行 UI 拉线只会让它更不可读。
 * 本类只负责"类型 + 参数"两个下拉的联动与持久化，读写全部走
 * {@link ReaderSourceStore}（单一真相源），自己不碰 SharedPreferences。</p>
 *
 * <p>2.3.19.4：两个下拉从裸 Spinner 换成 Material exposed dropdown
 * （{@link SettingDropdown}），与项目里既有的 TextInputLayout 输入框同一套视觉。</p>
 */
public final class ReaderSourceSectionBinder {

    /** 展示顺序 = 存储类型顺序，新增来源在这里加一项即可（TYPES 与 TYPE_LABELS 必须等长）。 */
    private static final String[] TYPES = {
            ReaderSourceSpec.TYPE_ALL,
            ReaderSourceSpec.TYPE_PINNED,
            ReaderSourceSpec.TYPE_TAG,
            ReaderSourceSpec.TYPE_SUBJECT
    };

    private static final int[] TYPE_LABELS = {
            R.string.reader_source_type_all,
            R.string.reader_source_type_pinned,
            R.string.reader_source_type_tag,
            R.string.reader_source_type_subject
    };

    private ReaderSourceSectionBinder() {
        // utility class
    }

    public static void bind(Fragment host, View root) {
        ViewGroup container = sectionContainer(root);
        if (container == null) return;

        final Context ctx = host.requireContext();
        final Context app = ctx.getApplicationContext();

        final List<String> tags = loadTagNames(app);
        final List<String> subjectTitles = new ArrayList<>();
        final List<Long> subjectIds = new ArrayList<>();
        for (Subject subject : loadSubjects(app)) {
            subjectTitles.add(subject.getTitle());
            subjectIds.add(subject.getId());
        }

        container.addView(sectionTitle(ctx));

        final SettingDropdown typeDropdown = SettingDropdown.attach(ctx, container)
                .setHint(R.string.reader_source_hint_type);
        final SettingDropdown paramDropdown = SettingDropdown.attach(ctx, container)
                .setHint(R.string.reader_source_hint_param);

        List<String> typeLabels = new ArrayList<>(TYPE_LABELS.length);
        for (int labelRes : TYPE_LABELS) {
            typeLabels.add(ctx.getString(labelRes));
        }

        ReaderSourceSpec current = ReaderSourceStore.load(app);
        typeDropdown.setOptions(typeLabels, typeIndex(current.getType()));
        applyParamOptions(paramDropdown, current, tags, subjectTitles, subjectIds);

        typeDropdown.setOnSelected(position -> {
            ReaderSourceSpec next = specForType(TYPES[position], tags, subjectIds);
            ReaderSourceStore.save(app, next);
            applyParamOptions(paramDropdown, next, tags, subjectTitles, subjectIds);
        });

        paramDropdown.setOnSelected(position -> {
            ReaderSourceSpec spec = ReaderSourceStore.load(app);
            if (ReaderSourceSpec.TYPE_TAG.equals(spec.getType()) && position < tags.size()) {
                ReaderSourceStore.save(app, ReaderSourceSpec.tag(tags.get(position)));
            } else if (ReaderSourceSpec.TYPE_SUBJECT.equals(spec.getType()) && position < subjectIds.size()) {
                ReaderSourceStore.save(app, ReaderSourceSpec.subject(subjectIds.get(position)));
            }
        });
    }

    /** 参数下拉随类型切换：标签列表 / 主题列表 / 不适用则整块隐藏（不摆"（无需选择）"占位）。 */
    private static void applyParamOptions(SettingDropdown paramDropdown, ReaderSourceSpec spec,
                                          List<String> tags, List<String> subjectTitles,
                                          List<Long> subjectIds) {
        String type = spec == null ? ReaderSourceSpec.TYPE_ALL : spec.getType();
        if (ReaderSourceSpec.TYPE_TAG.equals(type)) {
            paramDropdown.setOptions(tags, Math.max(0, tags.indexOf(spec.getParam())));
        } else if (ReaderSourceSpec.TYPE_SUBJECT.equals(type)) {
            int index = subjectIds.indexOf(parseLong(spec.getParam()));
            paramDropdown.setOptions(subjectTitles, Math.max(0, index));
        } else {
            paramDropdown.setOptions(new ArrayList<>(), 0);
        }
    }

    private static ReaderSourceSpec specForType(String type, List<String> tags, List<Long> subjectIds) {
        if (ReaderSourceSpec.TYPE_TAG.equals(type)) {
            return tags.isEmpty() ? ReaderSourceSpec.all() : ReaderSourceSpec.tag(tags.get(0));
        }
        if (ReaderSourceSpec.TYPE_SUBJECT.equals(type)) {
            return subjectIds.isEmpty() ? ReaderSourceSpec.all() : ReaderSourceSpec.subject(subjectIds.get(0));
        }
        return new ReaderSourceSpec(type, "");
    }

    private static int typeIndex(String type) {
        for (int i = 0; i < TYPES.length; i++) {
            if (TYPES[i].equals(type)) return i;
        }
        return 0;
    }

    private static long parseLong(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (Exception e) {
            return -1L;
        }
    }

    private static List<String> loadTagNames(Context app) {
        try {
            return new LinkDao(DbConnection.get(app).writable()).getAllTags();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static List<Subject> loadSubjects(Context app) {
        try {
            return new SubjectDao(DbConnection.get(app).writable()).getAllSubjects();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static TextView sectionTitle(Context ctx) {
        TextView title = new TextView(ctx);
        title.setText(R.string.reader_source_section_title);
        title.setTextSize(18f);
        int dp = (int) ctx.getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 24 * dp;
        lp.bottomMargin = 8 * dp;
        title.setLayoutParams(lp);
        return title;
    }

    private static ViewGroup sectionContainer(View root) {
        if (!(root instanceof ScrollView)) return null;
        ScrollView scroll = (ScrollView) root;
        if (scroll.getChildCount() == 0) return null;
        View child = scroll.getChildAt(0);
        return child instanceof ViewGroup ? (ViewGroup) child : null;
    }
}
