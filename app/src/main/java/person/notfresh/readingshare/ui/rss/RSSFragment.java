package person.notfresh.readingshare.ui.rss;

import static android.content.Context.CLIPBOARD_SERVICE;
import static androidx.core.content.ContextCompat.getSystemService;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import person.notfresh.readingshare.R;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import android.util.Log;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import person.notfresh.readingshare.model.RssEntry;
import person.notfresh.readingshare.model.RssSource;
import person.notfresh.readingshare.db.RssDao;
import android.widget.Toast;
import java.net.HttpURLConnection;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.app.AlertDialog;
import android.content.ComponentName;
import java.io.IOException;

public class RSSFragment extends Fragment {

    private static final String TAG = "RSSFragment";
    private static final int PAGE_SIZE = 10;
    private int currentPage = 0;
    private boolean isLoading = false;
    private boolean hasMoreData = true;

    private EditText rssUrlInput;
    private Button fetchButton;
    private RecyclerView rssRecyclerView;
    private RSSAdapter rssAdapter;
    private List<RssEntry> rssEntries = new ArrayList<>();
    private Spinner sourceSpinner;
    private RssDao rssDao;
    private List<RssSource> rssSources;
    private Button loadMoreButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.fragment_rss, container, false);

            rssDao = new RssDao(requireContext());
            sourceSpinner = view.findViewById(R.id.rss_source_spinner);
            rssRecyclerView = view.findViewById(R.id.rss_recycler_view);
            
            rssRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            rssAdapter = new RSSAdapter(getContext(), rssEntries);
            rssRecyclerView.setAdapter(rssAdapter);

            loadRssSources();

            // 获取上次选择的RSS源位置
            android.content.SharedPreferences prefs = requireActivity().getPreferences(android.content.Context.MODE_PRIVATE);
            int lastSelectedPosition = prefs.getInt("last_selected_rss_position", 0);
            
            // 设置spinner到上次选择的位置
            sourceSpinner.post(() -> {
                if(lastSelectedPosition < sourceSpinner.getAdapter().getCount()) {
                    sourceSpinner.setSelection(lastSelectedPosition);
                    // 加载对应源的内容
                    if(lastSelectedPosition >= 0 && lastSelectedPosition < rssSources.size()) {
                        RssSource lastSource = rssSources.get(lastSelectedPosition);
                        loadEntriesForSource(lastSource);
                    }
                }
            });

            // 保存用户的选择
            sourceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    android.content.SharedPreferences.Editor editor = prefs.edit();
                    editor.putInt("last_selected_rss_position", position);
                    editor.apply();
                    
                    if (position >= 0 && position < rssSources.size()) {
                        RssSource source = rssSources.get(position);
                        loadEntriesForSource(source);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    Log.d(TAG, "onNothingSelected: No source selected");
                }
            });

            loadMoreButton = view.findViewById(R.id.load_more_button);
            loadMoreButton.setOnClickListener(v -> {
                loadMoreButton.setEnabled(false); // 防止重复点击
                loadMoreEntries(); // 不再传递 ID 参数
            });

            // 初始隐藏加载更多按钮
            loadMoreButton.setVisibility(View.GONE);

            // 设置RecyclerView滚动监听
            rssRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    
                    LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                    // 当滚动到最后几项时显示加载更多按钮
                    if (hasMoreData && !isLoading && 
                        (visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 3) {
                        loadMoreButton.setVisibility(View.VISIBLE);
                    }
                }
            });

            return view;
        } catch (Exception e) {
            Log.e(TAG, "onCreateView: Error creating view", e);
            throw e;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);  // 启用选项菜单
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.rss_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_add_rss) {
            showAddRssDialog();
            return true;
        } else if (item.getItemId() == R.id.action_share_sources) {
            shareRssSources();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadRssSources() {
        try {
            Log.d(TAG, "loadRssSources: Starting to load RSS sources");
            rssSources = rssDao.getAllSources();
            Log.d(TAG, "loadRssSources: Found " + rssSources.size() + " sources");

            if (rssSources.isEmpty()) {
                Log.d(TAG, "loadRssSources: No sources found, adding default source");
                RssSource defaultSource = new RssSource("https://www.cnblogs.com/tuyile006/p/3691024.html", "目前没有RSS源");
                long sourceId = rssDao.insertSource(defaultSource);
                defaultSource.setId((int) sourceId);
                rssSources.add(defaultSource);
            }

            ArrayAdapter<RssSource> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                rssSources
            );
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            sourceSpinner.setAdapter(adapter);
            
            sourceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position >= 0 && position < rssSources.size()) {
                        Log.d(TAG, "onItemSelected: Selected source at position " + position);
                        RssSource source = rssSources.get(position);
                        loadEntriesForSource(source);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    Log.d(TAG, "onNothingSelected: No source selected");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "loadRssSources: Error loading sources", e);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> 
                    Toast.makeText(getContext(), "加载RSS源失败: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }
        }
    }

    private void loadEntriesForSource(RssSource source) {
        if (source == null) return;
        
        try {
            // 重置分页状态
            currentPage = 0;
            hasMoreData = true;
            rssEntries.clear();
            rssAdapter.notifyDataSetChanged();
            loadMoreButton.setVisibility(View.GONE); // 重置时隐藏按钮
            
            loadMoreEntries();
        } catch (Exception e) {
            Log.e(TAG, "loadEntriesForSource: Error loading entries", e);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> 
                    Toast.makeText(getContext(), "加载RSS内容失败", Toast.LENGTH_SHORT).show()
                );
            }
        }
    }

    private void loadMoreEntries() {
        if (isLoading || !hasMoreData) return;
        if (sourceSpinner == null || sourceSpinner.getSelectedItemPosition() < 0 || 
            rssSources == null || rssSources.isEmpty()) {
            return;
        }

        isLoading = true;
        loadMoreButton.setEnabled(false); // 禁用按钮防止重复点击
        
        try {
            RssSource currentSource = rssSources.get(sourceSpinner.getSelectedItemPosition());
            if (currentSource == null) {
                isLoading = false;
                loadMoreButton.setEnabled(true);
                return;
            }

            new Thread(() -> {
                try {
                    List<RssEntry> newEntries = rssDao.getEntriesForSource(
                        currentSource.getId(), 
                        currentPage * PAGE_SIZE, 
                        PAGE_SIZE
                    );
                    
                    if (newEntries.size() < PAGE_SIZE) {
                        hasMoreData = false;
                    }

                    if (!newEntries.isEmpty()) {
                        currentPage++;
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                try {
                                    int startPos = rssEntries.size();
                                    rssEntries.addAll(newEntries);
                                    rssAdapter.notifyItemRangeInserted(startPos, newEntries.size());
                                    
                                    // 更新按钮状态
                                    loadMoreButton.setEnabled(true);
                                    loadMoreButton.setVisibility(hasMoreData ? View.VISIBLE : View.GONE);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error updating UI with new entries", e);
                                }
                            });
                        }
                    } else {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                loadMoreButton.setVisibility(View.GONE);
                                if (currentPage == 0) {
                                    Toast.makeText(getContext(), "没有更多内容", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "loadMoreEntries: Error loading more entries", e);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "加载更多内容失败", Toast.LENGTH_SHORT).show();
                            loadMoreButton.setEnabled(true);
                        });
                    }
                } finally {
                    isLoading = false;
                }
            }).start();
        } catch (Exception e) {
            Log.e(TAG, "loadMoreEntries: Error starting load thread", e);
            isLoading = false;
            loadMoreButton.setEnabled(true);
        }
    }

    private void showAddRssDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_rss, null);
        EditText urlInput = dialogView.findViewById(R.id.rss_url_input);

        new AlertDialog.Builder(requireContext())
            .setTitle("添加RSS源")
            .setView(dialogView)
            .setPositiveButton("添加", (dialog, which) -> {
                String url = urlInput.getText().toString().trim();
                if (!url.isEmpty()) {
                    fetchRssFeed(url);
                    // ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    // clipboard.setPrimaryClip(ClipData.newPlainText("", ""));
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void fetchRssFeed(String urlString) {
        if (urlString.isEmpty()) {
            return;
        }

        Log.d("RSSFragment", "fetchRssFeed: Starting to fetch from URL: " + urlString);
        new Thread(() -> {
            try {
                Log.d("RSSFragment", "开始获取RSS源: " + urlString);
                URL url = new URL(urlString);
                // 使用 HttpURLConnection 获取 RSS 内容
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout(10000);
                conn.setConnectTimeout(15000);
                conn.setRequestMethod("GET");
                conn.setDoInput(true);
                
                Log.d("RSSFragment", "开始连接...");
                conn.connect();
                int responseCode = conn.getResponseCode();
                Log.d("RSSFragment", "HTTP响应码: " + responseCode);
                
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP error code: " + responseCode);
                }

                InputStream inputStream = conn.getInputStream();
                // 使用 XML Pull Parser 解析 RSS
                XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                XmlPullParser parser = factory.newPullParser();
                parser.setInput(new InputStreamReader(inputStream));

                List<RssEntry> entries = new ArrayList<>();
                String feedTitle = "";
                String currentTag = "";
                RssEntry currentEntry = null;
                
                Log.d("RSSFragment", "开始解析XML...");
                
                int eventType = parser.getEventType();
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        currentTag = parser.getName();
                        Log.d("RSSFragment", "解析标签: " + currentTag);
                        
                        if ("item".equals(currentTag) || "entry".equals(currentTag)) {
                            currentEntry = new RssEntry();
                        } else if ("title".equals(currentTag)) {
                            if (currentEntry == null && feedTitle.isEmpty()) {
                                feedTitle = parser.nextText();
                            } else if (currentEntry != null) {
                                currentEntry.setTitle(parser.nextText());
                            }
                        } else if ("link".equals(currentTag) && currentEntry != null) {
                            String href = parser.getAttributeValue(null, "href");
                            if (href != null) {
                                currentEntry.setLink(href);
                            } else {
                                currentEntry.setLink(parser.nextText());
                            }
                        } else if ("pubDate".equals(currentTag) && currentEntry != null) {
                            String dateStr = parser.nextText();
                            try {
                                SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
                                currentEntry.setPublishedDate(format.parse(dateStr));
                            } catch (ParseException e) {
                                currentEntry.setPublishedDate(new Date());
                            }
                        }
                    } else if (eventType == XmlPullParser.END_TAG) {
                        if ("item".equals(parser.getName()) || "entry".equals(parser.getName())) {
                            if (currentEntry != null) {
                                Log.d("RSSFragment", "添加条目: " + currentEntry.getTitle());
                                entries.add(currentEntry);
                                currentEntry = null;
                            }
                        }
                    }
                    eventType = parser.next();
                }
                
                Log.d("RSSFragment", "解析完成，找到 " + entries.size() + " 个条目");
                
                // 保存RSS源
                RssSource newSource = new RssSource(urlString, feedTitle.isEmpty() ? urlString : feedTitle);
                long sourceId = rssDao.insertSource(newSource);
                newSource.setId((int) sourceId);
                Log.d("ABC", "save source: save RSS source" + sourceId +" source title " + newSource.getName());

                // 保存所有条目
                for (RssEntry entry : entries) {
                    rssDao.insertEntry(entry, (int) sourceId);
                }

                Log.d("ABC", "fetchRssFeed: Saved source and " + entries.size() + " entries to database");

                // 更新UI显示
                rssEntries.clear();
                int itemsToShow = Math.min(entries.size(), PAGE_SIZE);
                rssEntries.addAll(entries.subList(0, itemsToShow));

                // 更新源列表
                List<RssSource> updatedSources = rssDao.getAllSources();

                getActivity().runOnUiThread(() -> {
                    rssAdapter.notifyDataSetChanged();

                    // 更新Spinner
                    ArrayAdapter<RssSource> adapter = (ArrayAdapter<RssSource>) sourceSpinner.getAdapter();
                    adapter.clear();
                    adapter.addAll(updatedSources);
                    adapter.notifyDataSetChanged();

                    // 选中新添加的源
                    sourceSpinner.setSelection(adapter.getPosition(newSource));

                    Toast.makeText(getContext(), "RSS源添加成功!", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e("RSSFragment", "RSS源获取失败", e);
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), 
                        "获取RSS源失败: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void shareRssSources() {
        List<RssSource> sources = rssDao.getAllSources();
        if (sources.isEmpty()) {
            Toast.makeText(requireContext(), "没有RSS源可分享", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder shareText = new StringBuilder("RSS源列表：\n\n");
        for (RssSource source : sources) {
            shareText.append(source.getName())
                    .append("\n")
                    .append(source.getUrl())
                    .append("\n\n");
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
        
        // 创建选择器并排除自己的应用
        Intent chooserIntent = Intent.createChooser(shareIntent, "分享RSS源");
        String myPackageName = requireContext().getPackageName();
        chooserIntent.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, 
            new ComponentName[]{new ComponentName(myPackageName, myPackageName + ".MainActivity")});
        
        startActivity(chooserIntent);
    }
} 