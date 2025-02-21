package person.notfresh.readingshare.ui.statistics;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.kizitonwose.calendarview.CalendarView;
import com.kizitonwose.calendarview.model.CalendarDay;
import com.kizitonwose.calendarview.model.CalendarMonth;
import com.kizitonwose.calendarview.model.DayOwner;
import com.kizitonwose.calendarview.ui.DayBinder;
import com.kizitonwose.calendarview.ui.MonthHeaderFooterBinder;
import com.kizitonwose.calendarview.ui.ViewContainer;

import java.time.YearMonth;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.databinding.FragmentStatisticsBinding;
import person.notfresh.readingshare.db.LinkDao;

public class StatisticsFragment extends Fragment {

    private static final String TAG = "StatisticsFragment";
    private FragmentStatisticsBinding binding;
    private LinkDao linkDao;
    private Map<String, Integer> dailyStatistics;
    private DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy年MM月");
    private DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                            ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView: 开始创建统计视图");
        binding = FragmentStatisticsBinding.inflate(inflater, container, false);
        
        linkDao = new LinkDao(requireContext());
        linkDao.open();
        
        // 获取统计数据
        dailyStatistics = linkDao.getDailyStatistics();
        Log.d(TAG, "onCreateView: 获取到统计数据，数据大小: " + dailyStatistics.size());
        for (Map.Entry<String, Integer> entry : dailyStatistics.entrySet()) {
            Log.d(TAG, "统计数据: " + entry.getKey() + " -> " + entry.getValue());
        }
        
        setupCalendarView();
        
        return binding.getRoot();
    }

    private void setupCalendarView() {
        Log.d(TAG, "setupCalendarView: 开始设置日历视图");
        CalendarView calendarView = binding.calendarView;
        
        // 设置日历范围
        YearMonth currentMonth = YearMonth.now();
        YearMonth firstMonth = currentMonth.minusMonths(6);
        YearMonth lastMonth = currentMonth.plusMonths(6);
        Log.d(TAG, "setupCalendarView: 日历范围 " + firstMonth + " 到 " + lastMonth);
        
        calendarView.setup(firstMonth, lastMonth, DayOfWeek.MONDAY);
        calendarView.scrollToMonth(currentMonth);

        // 设置日期单元格绑定器
        calendarView.setDayBinder(new DayBinder<DayViewContainer>() {
            @Override
            public DayViewContainer create(View view) {
                return new DayViewContainer(view);
            }

            @Override
            public void bind(DayViewContainer container, CalendarDay day) {
                Log.d(TAG, "bind: 绑定日期 " + day.getDate());
                container.day = day;  // 保存日期到容器中
                // 检查日期是否属于当前月份
                if (day.getOwner() != DayOwner.THIS_MONTH) {
                    container.dayText.setVisibility(View.INVISIBLE);
                    container.countText.setVisibility(View.GONE);
                    return;
                }

                // 显示当月日期
                container.dayText.setVisibility(View.VISIBLE);
                container.dayText.setText(String.valueOf(day.getDate().getDayOfMonth()));
                
                // 获取当天的统计数据
                String dateKey = day.getDate().format(dayFormatter);
                Log.d(TAG, "bind: 查找日期 " + dateKey + " 的统计数据");
                Integer count = dailyStatistics.get(dateKey);
                Log.d(TAG, "bind: 该日期的统计数据: " + count);
                if (count != null && count > 0) {
                    container.countText.setVisibility(View.VISIBLE);
                    container.countText.setText(String.valueOf(count));
                } else {
                    container.countText.setVisibility(View.GONE);
                }
            }
        });

        // 设置月份头部绑定器
        calendarView.setMonthHeaderBinder(new MonthHeaderFooterBinder<MonthViewContainer>() {
            @Override
            public MonthViewContainer create(View view) {
                return new MonthViewContainer(view);
            }

            @Override
            public void bind(MonthViewContainer container, CalendarMonth month) {
                container.headerText.setText(month.getYearMonth().format(monthFormatter));
            }
        });
    }

    static class DayViewContainer extends ViewContainer {
        TextView dayText;
        TextView countText;
        private CalendarDay day;

        DayViewContainer(View view) {
            super(view);
            dayText = view.findViewById(R.id.dayText);
            countText = view.findViewById(R.id.countText);
            
            view.setOnClickListener(v -> {
                if (day != null && day.getOwner() == DayOwner.THIS_MONTH) {
                    // 返回首页并传递日期参数
                    Bundle args = new Bundle();
                    args.putString("selected_date", day.getDate().format(DateTimeFormatter.ISO_DATE));
                    Navigation.findNavController(view)
                            .navigate(R.id.action_nav_statistics_to_nav_home, args);
                }
            });
        }
    }

    static class MonthViewContainer extends ViewContainer {
        TextView headerText;

        MonthViewContainer(View view) {
            super(view);
            headerText = view.findViewById(R.id.headerText);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (linkDao != null) {
            linkDao.close();
        }
        binding = null;
    }
} 