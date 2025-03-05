package person.notfresh.readingshare;

import android.database.Cursor;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.tabs.TabLayout;
import person.notfresh.readingshare.adapter.ClickStatisticsAdapter;
import person.notfresh.readingshare.db.LinkDao;

public class ClickStatisticsActivity extends AppCompatActivity {

    private LinkDao linkDao;
    private RecyclerView recyclerView;
    private ClickStatisticsAdapter adapter;
    private TabLayout tabLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_click_statistics);

        linkDao = new LinkDao(this);
        linkDao.open();

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        tabLayout = findViewById(R.id.tabLayout);
        tabLayout.addTab(tabLayout.newTab().setText("按月统计"));
        tabLayout.addTab(tabLayout.newTab().setText("按周统计"));

        loadData("month");

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                String period = tab.getPosition() == 0 ? "month" : "week";
                loadData(period);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void loadData(String period) {
        Cursor cursor = linkDao.getClickStatistics(period);
        adapter = new ClickStatisticsAdapter(cursor);
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        linkDao.close();
    }
} 