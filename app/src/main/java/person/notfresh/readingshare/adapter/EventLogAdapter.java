package person.notfresh.readingshare.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.eventlog.EventRecord;

public class EventLogAdapter extends RecyclerView.Adapter<EventLogAdapter.ViewHolder> {

    public interface Listener {
        void onEventClick(EventRecord item);
    }

    private final List<EventRecord> items;
    private final Listener listener;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());

    public EventLogAdapter(List<EventRecord> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EventRecord item = items.get(position);
        holder.action.setText(item.getAction().name());
        holder.topic.setText(item.getTopic());
        holder.entityId.setText(item.getEntityId());
        holder.processTime.setText("process: " + item.getProcessTime());
        holder.eventTime.setText("event:   " + item.getEventTime());
        holder.itemView.setOnClickListener(v -> listener.onEventClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView action;
        final TextView topic;
        final TextView entityId;
        final TextView processTime;
        final TextView eventTime;

        ViewHolder(View view) {
            super(view);
            action = view.findViewById(R.id.event_action);
            topic = view.findViewById(R.id.event_topic);
            entityId = view.findViewById(R.id.event_entity_id);
            processTime = view.findViewById(R.id.event_process_time);
            eventTime = view.findViewById(R.id.event_event_time);
        }
    }
}
