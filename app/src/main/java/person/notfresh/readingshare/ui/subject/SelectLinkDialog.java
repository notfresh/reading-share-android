package person.notfresh.readingshare.ui.subject;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.core.model.SubjectItem;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.db.SubjectDao;
import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.util.LinkSearchSpec;

/**
 * 主题详情页的链接选择器。
 *
 * <p>职责：协调 {@link SelectLinkAdapter}、{@link LinkDao}、{@link SubjectDao}。
 * <ul>
 *   <li>默认列表：按 timestamp DESC 分页加载（前 {@link #PAGE_SIZE} 条 + 滚动加载更多）。</li>
 *   <li>搜索：{@code title:foo} / {@code tag:bar} / {@code url:baz} 限定维度；否则全字段 OR。一次最多 {@link #SEARCH_LIMIT} 条。</li>
 *   <li>已收录集合：每次 {@link #onResume()} 从 SubjectDao 同步一次，用于置灰。</li>
 *   <li>点击：未收录 → 选中后回调；已收录 → 取消收录。</li>
 * </ul>
 */
public class SelectLinkDialog extends DialogFragment {

    private static final String TAG = "SelectLinkDialog";
    private static final String ARG_SUBJECT_ID = "subject_id";

    /** 默认分页大小。 */
    private static final int PAGE_SIZE = 100;
    /** 搜索结果上限（搜索不分页，避免大数据集加载慢）。 */
    private static final int SEARCH_LIMIT = 200;

    /** 回调：把选中的 linkId 抛给上游（通常是 AddSubjectItemDialog）。 */
    public interface OnLinkSelectedListener {
        void onLinkSelected(long linkId, String title, String url);
    }

    public static SelectLinkDialog newInstance(long subjectId) {
        SelectLinkDialog d = new SelectLinkDialog();
        Bundle args = new Bundle();
        args.putLong(ARG_SUBJECT_ID, subjectId);
        d.setArguments(args);
        return d;
    }

    private long subjectId;
    private LinkDao linkDao;
    private SubjectDao subjectDao;
    private ExecutorService io;

    // UI
    private SelectLinkAdapter adapter;
    private RecyclerView recyclerView;
    private TextInputEditText searchInput;
    private TextView emptyView;
    private Button loadMoreButton;
    private TextView hintView;

    // 状态
    private int pageOffset = 0;
    private boolean hasMore = true;
    private boolean loading = false;
    private String lastQuery = "";
    private final Set<Long> collectedLinkIds = new HashSet<>();
    private OnLinkSelectedListener listener;

    public void setOnLinkSelectedListener(OnLinkSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        subjectId = getArguments() != null ? getArguments().getLong(ARG_SUBJECT_ID, -1L) : -1L;
        linkDao = new LinkDao(requireContext());
        linkDao.open();
        subjectDao = new SubjectDao(requireContext());
        subjectDao.open();
        io = Executors.newSingleThreadExecutor();
        refreshCollectedIds();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Dialog 重新可见时刷新已收录集合（用户在主页面可能加了/删了）
        refreshCollectedIds();
        adapter.setDisabledIds(collectedLinkIds);
    }

    private void refreshCollectedIds() {
        if (subjectId <= 0 || subjectDao == null) return;
        List<SubjectItem> items = subjectDao.getSubjectItemsBySubjectId(subjectId);
        collectedLinkIds.clear();
        for (SubjectItem si : items) {
            Long lid = si.getLinkId();
            if (lid != null && lid > 0) collectedLinkIds.add(lid);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = View.inflate(requireContext(), R.layout.dialog_select_link, null);
        recyclerView = view.findViewById(R.id.picker_recycler);
        searchInput = view.findViewById(R.id.picker_search_input);
        emptyView = view.findViewById(R.id.picker_empty);
        loadMoreButton = view.findViewById(R.id.picker_load_more);
        hintView = view.findViewById(R.id.picker_hint);

        adapter = new SelectLinkAdapter();
        adapter.setOnLinkClickListener(this::handleLinkClick);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
        adapter.setDisabledIds(collectedLinkIds);

        loadMoreButton.setOnClickListener(v -> loadNextPage());

        // 滚动到底自动加载
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (loading || !lastQuery.isEmpty() || !hasMore) return;
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                int lastVisible = lm.findLastVisibleItemPosition();
                if (lastVisible >= adapter.getItemCount() - 5) {
                    loadNextPage();
                }
            }
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                String text = s == null ? "" : s.toString();
                if (text.equals(lastQuery)) return;
                lastQuery = text;
                resetAndLoad();
            }
        });

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("选择链接")
                .setView(view)
                .setNegativeButton("关闭", null)
                .create();

        resetAndLoad();
        return dialog;
    }

    /**
     * 重置分页 + 加载第一页（或搜索结果）。
     */
    private void resetAndLoad() {
        pageOffset = 0;
        hasMore = true;
        adapter.setItems(Collections.emptyList());
        if (lastQuery.isEmpty()) {
            loadNextPage();
        } else {
            // 搜索：一次性 in-memory，一次性填充，不显示"加载更多"
            runSearch();
        }
    }

    private void loadNextPage() {
        if (loading || !hasMore || !lastQuery.isEmpty()) return;
        loading = true;
        final int offset = pageOffset;
        io.execute(() -> {
            List<LinkItem> page = linkDao.getLinksPage(offset, PAGE_SIZE);
            requireActivity().runOnUiThread(() -> {
                if (page.size() < PAGE_SIZE) hasMore = false;
                pageOffset += page.size();
                if (offset == 0) adapter.setItems(page); else adapter.appendItems(page);
                loadMoreButton.setVisibility(hasMore ? View.VISIBLE : View.GONE);
                loading = false;
                updateEmptyView();
            });
        });
    }

    private void runSearch() {
        loading = true;
        loadMoreButton.setVisibility(View.GONE);
        io.execute(() -> {
            LinkSearchSpec spec = LinkSearchSpec.parse(lastQuery, SEARCH_LIMIT);
            List<LinkItem> raw;
            try {
                raw = linkDao.searchLinks(spec);
            } catch (Exception e) {
                Log.e(TAG, "搜索失败: " + lastQuery, e);
                raw = new ArrayList<>();
            }
            final List<LinkItem> hits = raw;
            requireActivity().runOnUiThread(() -> {
                adapter.setItems(hits);
                loading = false;
                hasMore = false;
                updateEmptyView();
            });
        });
    }

    private void updateEmptyView() {
        boolean empty = adapter.getItemCount() == 0 && !loading;
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (hintView != null) hintView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /**
     * 单击行：未收录 → 选中 + 回调；已收录 → 取消收录 + 刷新置灰。
     */
    private void handleLinkClick(LinkItem item) {
        if (collectedLinkIds.contains(item.getId())) {
            removeFromSubject(item.getId());
        } else {
            if (listener != null) {
                listener.onLinkSelected(item.getId(), item.getTitle(), item.getUrl());
            }
            dismissAllowingStateLoss();
        }
    }

    private void removeFromSubject(long linkId) {
        // 找到当前 subjectId 下、指向该 linkId 的 SubjectItem.id，删除之
        io.execute(() -> {
            List<SubjectItem> items = subjectDao.getSubjectItemsBySubjectId(subjectId);
            int removed = 0;
            for (SubjectItem si : items) {
                Long lid = si.getLinkId();
                if (lid != null && lid == linkId && subjectDao.deleteSubjectItem(si.getId())) {
                    removed++;
                }
            }
            final int done = removed;
            requireActivity().runOnUiThread(() -> {
                refreshCollectedIds();
                adapter.setDisabledIds(collectedLinkIds);
                if (done > 0) {
                    Toast.makeText(requireContext(), "已取消收录 " + done + " 条", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (linkDao != null) linkDao.close();
        if (subjectDao != null) subjectDao.close();
        if (io != null) io.shutdown();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        recyclerView = null;
        searchInput = null;
        emptyView = null;
        loadMoreButton = null;
        hintView = null;
        adapter = null;
    }
}