package person.notfresh.myapplication.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import person.notfresh.myapplication.R;
import person.notfresh.myapplication.adapter.LinksAdapter;
import person.notfresh.myapplication.databinding.FragmentHomeBinding;
import person.notfresh.myapplication.db.LinkDao;
import person.notfresh.myapplication.model.LinkItem;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class HomeFragment extends Fragment implements LinksAdapter.OnLinkActionListener {

    private FragmentHomeBinding binding;
    private LinksAdapter adapter;
    private LinkDao linkDao;
    private boolean isSelectionMode = false;
    private MenuItem shareMenuItem;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        Log.d("HomeFragment", "onCreate: setHasOptionsMenu(true)");
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        Log.d("HomeFragment", "onCreateOptionsMenu");
        menu.clear();
        inflater.inflate(R.menu.home_menu, menu);
        shareMenuItem = menu.findItem(R.id.action_share);
        shareMenuItem.setVisible(isSelectionMode);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        Log.d("HomeFragment", "onOptionsItemSelected: " + item.getItemId());
        if (item.getItemId() == R.id.action_share) {
            Log.d("HomeFragment", "Share button clicked");
            shareSelectedItems();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                            ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        linkDao = new LinkDao(requireContext());
        linkDao.open();

        RecyclerView recyclerView = binding.recyclerView;
        adapter = new LinksAdapter(requireContext());
        adapter.setOnLinkActionListener(this);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        // 加载按日期分组的链接
        Map<String, List<LinkItem>> groupedLinks = linkDao.getLinksGroupByDate();
        adapter.setGroupedLinks(groupedLinks);

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        if (linkDao != null) {
            linkDao.close();
        }
    }

    @Override
    public void onDeleteLink(LinkItem link) {
        linkDao.deleteLink(link.getUrl());
        // 刷新列表
        Map<String, List<LinkItem>> groupedLinks = linkDao.getLinksGroupByDate();
        adapter.setGroupedLinks(groupedLinks);
    }

    @Override
    public void onUpdateLink(LinkItem oldLink, String newTitle) {
        linkDao.updateLinkTitle(oldLink.getUrl(), newTitle);
        // 刷新列表
        Map<String, List<LinkItem>> groupedLinks = linkDao.getLinksGroupByDate();
        adapter.setGroupedLinks(groupedLinks);
    }

    @Override
    public void addTagToLink(LinkItem item, String tag) {
        linkDao.addTagToLink(item.getId(), tag);
        // 刷新列表
        Map<String, List<LinkItem>> groupedLinks = linkDao.getLinksGroupByDate();
        adapter.setGroupedLinks(groupedLinks);
    }

    @Override
    public void updateLinkTags(LinkItem item) {
        linkDao.updateLinkTags(item);
        // 刷新列表
        Map<String, List<LinkItem>> groupedLinks = linkDao.getLinksGroupByDate();
        adapter.setGroupedLinks(groupedLinks);
    }

    @Override
    public void onEnterSelectionMode() {
        if (!isSelectionMode) {
            toggleSelectionMode();
        }
    }

    private void toggleSelectionMode() {
        Log.d("HomeFragment", "toggleSelectionMode called");
        isSelectionMode = !isSelectionMode;
        adapter.toggleSelectionMode();
        if (shareMenuItem != null) {
            shareMenuItem.setVisible(isSelectionMode);
        }
        // 更新标题
        if (isSelectionMode) {
            requireActivity().setTitle("选择要分享的链接");
        } else {
            requireActivity().setTitle(R.string.app_name);
        }
        requireActivity().invalidateOptionsMenu();
        Log.d("HomeFragment", "Selection mode: " + isSelectionMode);
    }

    private void shareSelectedItems() {
        Set<LinkItem> selectedItems = adapter.getSelectedItems();
        if (selectedItems.isEmpty()) {
            Toast.makeText(requireContext(), "请先选择要分享的链接", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder shareText = new StringBuilder();
        for (LinkItem item : selectedItems) {
            shareText.append(item.getTitle())
                    .append("\n")
                    .append(item.getUrl())
                    .append("\n\n");
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
        startActivity(Intent.createChooser(shareIntent, "分享到"));
    }
}