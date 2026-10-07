package person.notfresh.readingshare.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.db.DbConnection;
import person.notfresh.readingshare.db.LinkDao;

/**
 * 标签输入联想:把 EditText 下面的 suggestion RecyclerView 接通起来。
 *
 * <p>使用方式(在 showAddTagDialog 内):
 * <pre>{@code
 *   EditText input = dialogView.findViewById(R.id.edit_tag_input);
 *   RecyclerView rv = dialogView.findViewById(R.id.suggestion_recycler);
 *   TextView title = dialogView.findViewById(R.id.text_suggestion_label);
 *   Set<String> excluded = new HashSet<>(existingTags);
 *   TagSuggestionHelper.attach(input, rv, title, requireContext(), excluded);
 * }</pre>
 *
 * <p>行为契约:
 * <ul>
 *   <li>每字符 + 150ms 防抖触发一次过滤</li>
 *   <li>取输入最后一个英文/中文逗号之后的非空片段,做 containsIgnoreCase 子串匹配</li>
 *   <li>匹配项从 {@code excluded} 集合排除</li>
 *   <li>无匹配:整块隐藏(title + recycler 都 gone)</li>
 *   <li>有匹配:整块显示,点击项 append 到输入框末尾(逗号分隔 + selection 到末尾)</li>
 *   <li>数据源异步加载(子线程 LinkDao.getAllTags),加载完成后触发一次筛选</li>
 * </ul>
 */
public final class TagSuggestionHelper {

    /** 防抖窗口(单位 ms)。短于搜索历史(300ms),因为联想要求更实时。 */
    static final long DEBOUNCE_MS = 150L;

    private TagSuggestionHelper() {}

    /** 解绑手柄。持有者(如 dialog)早释放前可调 detach()。 */
    public static final class Binder {
        private final EditText input;
        private final TextWatcher watcher;
        private final Runnable pending;

        Binder(EditText input, TextWatcher watcher, Runnable pending) {
            this.input = input;
            this.watcher = watcher;
            this.pending = pending;
        }

        public void detach() {
            if (input != null) {
                if (watcher != null) input.removeTextChangedListener(watcher);
                if (pending != null) input.removeCallbacks(pending);
            }
        }
    }

    /**
     * 把 suggestion RecyclerView 接入输入框。
     *
     * @param input    输入框
     * @param recycler 候选 RecyclerView(xml 默认 gone)
     * @param title    suggestion 标题 TextView(xml 默认 gone;无匹配也跟着隐藏)
     * @param ctx      Context(取 Dao 用)
     * @param excluded 要排除的标签集合(如当前 link 已有的 tag),传 null 视作空集
     * @return Binder,持有 watcher/pending,可调 detach() 提前收尾
     */
    public static Binder attach(EditText input,
                                RecyclerView recycler,
                                TextView title,
                                Context ctx,
                                Set<String> excluded) {
        if (input == null || recycler == null || ctx == null) {
            return new Binder(null, null, null);
        }
        Set<String> exclude = excluded != null ? excluded : new HashSet<>();
        SuggestionAdapter adapter = new SuggestionAdapter(ctx.getApplicationContext(),
                tag -> replaceLastSegment(input, tag));
        recycler.setLayoutManager(new LinearLayoutManager(ctx));
        recycler.setAdapter(adapter);
        applyTitleAndHiddenChannels(adapter, recycler, title, null, exclude);

        // 防抖 runnable:触发时取最新文本 → 过滤
        Runnable pending = () -> {
            String text = input.getText() == null ? "" : input.getText().toString();
            String segment = lastSegment(text);
            String query = segment;
            applyTitleAndHiddenChannels(adapter, recycler, title, query, exclude);
        };

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                input.removeCallbacks(pending);
                input.postDelayed(pending, DEBOUNCE_MS);
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        input.addTextChangedListener(watcher);

        // 一次性拉全量标签(异步)
        new TagSourceLoader(ctx.getApplicationContext(), all -> {
            adapter.setAllTags(all);
            // 数据集回来:再过滤一次(可能用户已经开始打字了)
            pending.run();
        }).start();

        return new Binder(input, watcher, pending);
    }

    /**
     * 同步触发一次过滤(供 TagSourceLoader 数据回来时调用 / 测试)。
     */
    private static void applyTitleAndHiddenChannels(SuggestionAdapter adapter,
                                                    RecyclerView recycler,
                                                    TextView title,
                                                    String query,
                                                    Set<String> exclude) {
        adapter.applyFilter(query, exclude);
        boolean hasItems = adapter.getItemCount() > 0;
        if (title != null) title.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        recycler.setVisibility(hasItems ? View.VISIBLE : View.GONE);
    }

    /** 取输入的最后一个逗号段(逗号之后的非空片段)。 */
    private static String lastSegment(String text) {
        if (text == null || text.isEmpty()) return "";
        int idx = Math.max(text.lastIndexOf(','), text.lastIndexOf('，'));
        if (idx < 0) return text;
        return text.substring(idx + 1);
    }

    /**
     * 候选点击后:替换输入框里最后一个逗号段(逗号之前的都保留,逗号也保留)。
     * 没有逗号 → 整段替换。
     * <p>
     * 例子:
     * <ul>
     *   <li>"机器学习,深" 选 "深度学习" → "机器学习,深度学习"</li>
     *   <li>"深度学习"   选 "深度求索" → "深度求索"(整段替换)</li>
     * </ul>
     */
    private static void replaceLastSegment(EditText input, String tag) {
        String cur = input.getText() == null ? "" : input.getText().toString();
        int idx = Math.max(cur.lastIndexOf(','), cur.lastIndexOf('，'));
        String next;
        if (idx < 0) {
            // 没有逗号 → 整段替换
            next = tag;
        } else {
            // 保留逗号,替换其后的片段
            next = cur.substring(0, idx + 1) + tag;
        }
        input.setText(next);
        input.setSelection(input.getText().length());
    }

    // ========== RecyclerView Adapter ==========

    static final class SuggestionAdapter extends RecyclerView.Adapter<SuggestionAdapter.VH> {
        private final Context ctx;
        private final OnPick listener;
        private final List<String> allTags = new ArrayList<>();
        private final List<String> filtered = new ArrayList<>();

        SuggestionAdapter(Context ctx, OnPick listener) {
            this.ctx = ctx;
            this.listener = listener;
        }

        void setAllTags(List<String> tags) {
            allTags.clear();
            if (tags != null) allTags.addAll(tags);
        }

        /** query null/空 → 整块隐藏(由 caller 控制);非空 → 子串 contains 过滤,排除 exclude 项。 */
        void applyFilter(String query, Set<String> exclude) {
            filtered.clear();
            if (query == null) { notifyDataSetChanged(); return; }
            String q = query.trim().toLowerCase(Locale.US);
            if (q.isEmpty()) { notifyDataSetChanged(); return; }
            for (String tag : allTags) {
                if (tag == null) continue;
                if (exclude != null && exclude.contains(tag)) continue;
                if (tag.toLowerCase(Locale.US).contains(q)) filtered.add(tag);
            }
            notifyDataSetChanged();
        }

        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_tag_suggestion, parent, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(VH h, int position) {
            String tag = filtered.get(position);
            h.text.setText(tag);
            h.itemView.setOnClickListener(v -> listener.onPick(tag));
        }

        @Override public int getItemCount() { return filtered.size(); }

        static final class VH extends RecyclerView.ViewHolder {
            final TextView text;
            VH(View itemView) {
                super(itemView);
                text = (TextView) itemView;
            }
        }

        interface OnPick { void onPick(String tag); }
    }

    // ========== 异步加载全量标签 ==========

    private static final class TagSourceLoader {
        private final Context appCtx;
        private final OnReady cb;

        TagSourceLoader(Context ctx, OnReady cb) {
            this.appCtx = ctx.getApplicationContext();
            this.cb = cb;
        }

        void start() {
            new Thread(() -> {
                List<String> all;
                try {
                    LinkDao dao = new LinkDao(DbConnection.get(appCtx).writable());
                    all = dao.getAllTags();
                } catch (Throwable t) {
                    all = new ArrayList<>();
                }
                final List<String> result = all;
                new Handler(Looper.getMainLooper()).post(() -> cb.onReady(result));
            }).start();
        }

        interface OnReady { void onReady(List<String> allTags); }
    }
}