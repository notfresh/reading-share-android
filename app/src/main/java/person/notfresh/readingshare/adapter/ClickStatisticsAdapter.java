package person.notfresh.readingshare.adapter;

import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import person.notfresh.readingshare.R;

public class ClickStatisticsAdapter extends RecyclerView.Adapter<ClickStatisticsAdapter.ViewHolder> {

    private Cursor cursor;

    public ClickStatisticsAdapter(Cursor cursor) {
        this.cursor = cursor;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_click_statistic, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        if (cursor.moveToPosition(position)) {
            String period = cursor.getString(0);
            int totalClicks = cursor.getInt(1);
            holder.periodText.setText(period);
            holder.countText.setText(String.valueOf(totalClicks));
        }
    }

    @Override
    public int getItemCount() {
        return cursor.getCount();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView periodText;
        TextView countText;

        ViewHolder(View view) {
            super(view);
            periodText = view.findViewById(R.id.periodText);
            countText = view.findViewById(R.id.countText);
        }
    }
} 