package person.notfresh.readingshare.ui.rss;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.LayoutInflater;
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
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import android.util.Log;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import person.notfresh.readingshare.model.RssSource;
import person.notfresh.readingshare.db.RssDao;

public class RSSFragment extends Fragment {

    private static final String TAG = "RSSFragment";

    private EditText rssUrlInput;
    private Button fetchButton;
    private RecyclerView rssRecyclerView;
    private RSSAdapter rssAdapter;
    private List<SyndEntry> rssEntries = new ArrayList<>();
    private Spinner sourceSpinner;
    private RssDao rssDao;
    private List<RssSource> rssSources;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView: Starting to create view");
        try {
            View view = inflater.inflate(R.layout.fragment_rss, container, false);

            Log.d(TAG, "onCreateView: Initializing RssDao");
            rssDao = new RssDao(requireContext());

            Log.d(TAG, "onCreateView: Setting up Spinner");
            sourceSpinner = view.findViewById(R.id.rss_source_spinner);
            loadRssSources();

            Log.d(TAG, "onCreateView: Setting up other views");
            rssUrlInput = view.findViewById(R.id.rss_url_input);
            fetchButton = view.findViewById(R.id.fetch_button);
            rssRecyclerView = view.findViewById(R.id.rss_recycler_view);

            Log.d(TAG, "onCreateView: Setting up RecyclerView");
            rssRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            rssAdapter = new RSSAdapter(getContext(), rssEntries);
            rssRecyclerView.setAdapter(rssAdapter);

            fetchButton.setOnClickListener(v -> {
                fetchButton.setEnabled(false);
                fetchRssFeed();
            });

            Log.d(TAG, "onCreateView: View setup completed successfully");
            return view;
        } catch (Exception e) {
            Log.e(TAG, "onCreateView: Error creating view", e);
            throw e;
        }
    }

    private void loadRssSources() {
        try {
            Log.d(TAG, "loadRssSources: Starting to load RSS sources");
            rssSources = rssDao.getAllSources();
            Log.d(TAG, "loadRssSources: Found " + rssSources.size() + " sources");

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
                    Log.d(TAG, "onItemSelected: Selected source at position " + position);
                    RssSource source = rssSources.get(position);
                    loadEntriesForSource(source);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    Log.d(TAG, "onNothingSelected: No source selected");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "loadRssSources: Error loading sources", e);
        }
    }

    private void loadEntriesForSource(RssSource source) {
        try {
            Log.d(TAG, "loadEntriesForSource: Loading entries for source: " + source.getName());
            List<SyndEntry> entries = rssDao.getEntriesForSource(source.getId());
            Log.d(TAG, "loadEntriesForSource: Found " + entries.size() + " entries");
            rssAdapter.updateEntries(entries);
        } catch (Exception e) {
            Log.e(TAG, "loadEntriesForSource: Error loading entries", e);
        }
    }

    private void fetchRssFeed() {
        String urlString = rssUrlInput.getText().toString().trim();
        if (urlString.isEmpty()) {
            Log.d(TAG, "fetchRssFeed: Empty URL");
            fetchButton.setEnabled(true);
            return;
        }

        Log.d(TAG, "fetchRssFeed: Starting to fetch from URL: " + urlString);
        new Thread(() -> {
            try {
                URL url = new URL(urlString);
                SyndFeedInput input = new SyndFeedInput();
                SyndFeed feed = input.build(new XmlReader(url));

                Log.d(TAG, "fetchRssFeed: Successfully fetched feed");
                rssEntries.clear();
                rssEntries.addAll(feed.getEntries());
                Log.d(TAG, "fetchRssFeed: Added " + rssEntries.size() + " entries");

                getActivity().runOnUiThread(() -> {
                    rssAdapter.notifyDataSetChanged();
                    fetchButton.setEnabled(true);
                });
            } catch (Exception e) {
                Log.e(TAG, "fetchRssFeed: Error fetching feed", e);
                getActivity().runOnUiThread(() -> fetchButton.setEnabled(true));
            }
        }).start();
    }
} 