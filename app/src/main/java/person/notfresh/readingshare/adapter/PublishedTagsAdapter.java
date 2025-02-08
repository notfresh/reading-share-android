package person.notfresh.readingshare.adapter;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import person.notfresh.readingshare.R;

public class PublishedTagsAdapter extends RecyclerView.Adapter<PublishedTagsAdapter.ViewHolder> {
    private JSONArray publishedTags = new JSONArray();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    public void updateData(JSONArray newData) {
        if (newData == null) {
            publishedTags = new JSONArray();
        } else {
            publishedTags = newData;
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_published_tag, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        try {
            JSONObject item = publishedTags.optJSONObject(position);
            if (item == null) return;

            String tag = item.optString("tag", "未知标签");
            String url = item.optString("url", "");
            long timestamp = item.optLong("timestamp", System.currentTimeMillis());

            holder.tagName.setText(tag);
            holder.publishUrl.setText(url);
            holder.publishTime.setText(dateFormat.format(new Date(timestamp)));

            holder.itemView.setOnClickListener(v -> {
                if (!url.isEmpty()) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        v.getContext().startActivity(intent);
                    } catch (Exception e) {
                        Log.e("PublishedTagsAdapter", "Error opening URL", e);
                        Toast.makeText(v.getContext(), "无法打开链接", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            holder.shareButton.setOnClickListener(v -> {
                try {
                    Context context = v.getContext();
                    StringBuilder shareText = new StringBuilder();
                    shareText.append("标签: ").append(tag).append("\n");
                    shareText.append("发布时间: ").append(dateFormat.format(new Date(timestamp))).append("\n");
                    shareText.append("链接: ").append(url);

                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());

                    Intent chooserIntent = Intent.createChooser(shareIntent, "分享到");
                    String myPackageName = context.getPackageName();
                    chooserIntent.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, 
                        new ComponentName[]{new ComponentName(myPackageName, myPackageName + ".MainActivity")});
                    
                    context.startActivity(chooserIntent);
                } catch (Exception e) {
                    Log.e("PublishedTagsAdapter", "Error sharing tag", e);
                    Toast.makeText(v.getContext(), "分享失败", Toast.LENGTH_SHORT).show();
                }
            });

        } catch (Exception e) {
            Log.e("PublishedTagsAdapter", "Error binding view holder", e);
            // 设置一些默认值
            holder.tagName.setText("加载失败");
            holder.publishUrl.setText("");
            holder.publishTime.setText("");
        }
    }

    @Override
    public int getItemCount() {
        return publishedTags != null ? publishedTags.length() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tagName;
        TextView publishTime;
        TextView publishUrl;
        ImageButton shareButton;

        ViewHolder(View view) {
            super(view);
            tagName = view.findViewById(R.id.tag_name);
            publishTime = view.findViewById(R.id.publish_time);
            publishUrl = view.findViewById(R.id.publish_url);
            shareButton = view.findViewById(R.id.btn_share);
        }
    }
} 