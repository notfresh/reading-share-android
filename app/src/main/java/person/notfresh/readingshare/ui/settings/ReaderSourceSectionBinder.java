package person.notfresh.readingshare.ui.settings;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
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

        final Spinner typeSpinner = newSpinner(ctx);
        final Spinner paramSpinner = newSpinner(ctx);
        container.addView(typeSpinner);
        container.addView(paramSpinner);

        List<String> typeLabels = new ArrayList<>(TYPE_LABELS.length);
        for (int labelRes : TYPE_LABELS) {
            typeLabels.add(ctx.getString(labelRes));
        }
        typeSpinner.setAdapter(spinnerAdapter(ctx, typeLabels));

        ReaderSourceSpec current = ReaderSourceStore.load(app);
        final boolean[] suppress = {true};
        setParamOptions(ctx, paramSpinner, current, tags, subjectTitles, subjectIds);
        typeSpinner.setSelection(typeIndex(current.getType()), false);

        typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppress[0]) return;
                ReaderSourceSpec next = specForType(TYPES[position], tags, subjectIds);
                ReaderSourceStore.save(app, next);
                suppress[0] = true;
                setParamOptions(ctx, paramSpinner, next, tags, subjectTitles, subjectIds);
                suppress[0] = false;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        paramSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppress[0]) return;
                ReaderSourceSpec spec = ReaderSourceStore.load(app);
                if (ReaderSourceSpec.TYPE_TAG.equals(spec.getType()) && position < tags.size()) {
                    ReaderSourceStore.save(app, ReaderSourceSpec.tag(tags.get(position)));
                } else if (ReaderSourceSpec.TYPE_SUBJECT.equals(spec.getType()) && position < subjectIds.size()) {
                    ReaderSourceStore.save(app, ReaderSourceSpec.subject(subjectIds.get(position)));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        suppress[0] = false;
    }

    /** 参数下拉的内容随类型变化：标签列表 / 主题列表 / 占位提示。 */
    private static void setParamOptions(Context ctx, Spinner paramSpinner, ReaderSourceSpec spec,
                                       List<String> tags, List<String> subjectTitles,
                                       List<Long> subjectIds) {
        List<String> options = new ArrayList<>();
        int selected = 0;
        String type = spec == null ? ReaderSourceSpec.TYPE_ALL : spec.getType();
        if (ReaderSourceSpec.TYPE_TAG.equals(type)) {
            if (tags.isEmpty()) {
                options.add(ctx.getString(R.string.reader_source_empty_tags));
            } else {
                options.addAll(tags);
                selected = Math.max(0, tags.indexOf(spec.getParam()));
            }
        } else if (ReaderSourceSpec.TYPE_SUBJECT.equals(type)) {
            if (subjectIds.isEmpty()) {
                options.add(ctx.getString(R.string.reader_source_empty_subjects));
            } else {
                options.addAll(subjectTitles);
                int idx = subjectIds.indexOf(parseLong(spec.getParam()));
                selected = Math.max(0, idx);
            }
        } else {
            options.add(ctx.getString(R.string.reader_source_param_none));
        }
        paramSpinner.setAdapter(spinnerAdapter(ctx, options));
        paramSpinner.setSelection(selected, false);
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

    private static Spinner newSpinner(Context ctx) {
        Spinner spinner = new Spinner(ctx);
        spinner.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return spinner;
    }

    private static ArrayAdapter<String> spinnerAdapter(Context ctx, List<String> options) {
        return new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, options);
    }

    private static ViewGroup sectionContainer(View root) {
        if (!(root instanceof ScrollView)) return null;
        ScrollView scroll = (ScrollView) root;
        if (scroll.getChildCount() == 0) return null;
        View child = scroll.getChildAt(0);
        return child instanceof ViewGroup ? (ViewGroup) child : null;
    }
}
