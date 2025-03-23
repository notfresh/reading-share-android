package person.notfresh.readingshare.util;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

public class RecentTagsManager {
    private static final String PREF_NAME = "recent_tags_pref";
    private static final String KEY_RECENT_TAGS = "recent_tags";
    private static final String TAG_SEPARATOR = ",";
    private static final int MAX_RECENT_TAGS = 3;

    public static void addRecentTag(Context context, String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String recentTagsStr = prefs.getString(KEY_RECENT_TAGS, "");
        
        // 使用LinkedHashSet保持插入顺序并去重
        LinkedHashSet<String> tagsSet = new LinkedHashSet<>();
        
        // 将新标签添加到最前面
        tagsSet.add(tag.trim());
        
        // 添加现有标签
        if (!recentTagsStr.isEmpty()) {
            String[] existingTags = recentTagsStr.split(TAG_SEPARATOR);
            tagsSet.addAll(Arrays.asList(existingTags));
        }
        
        // 转换回列表并限制数量
        List<String> tagsList = new ArrayList<>(tagsSet);
        if (tagsList.size() > MAX_RECENT_TAGS) {
            tagsList = tagsList.subList(0, MAX_RECENT_TAGS);
        }
        
        // 保存回SharedPreferences
        StringBuilder newTagsStr = new StringBuilder();
        for (int i = 0; i < tagsList.size(); i++) {
            if (i > 0) newTagsStr.append(TAG_SEPARATOR);
            newTagsStr.append(tagsList.get(i));
        }
        
        prefs.edit().putString(KEY_RECENT_TAGS, newTagsStr.toString()).apply();
    }
    
    public static void addRecentTags(Context context, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return;
        }
        
        // 从最后一个标签开始添加，保证最新的标签在最前面
        for (int i = tags.size() - 1; i >= 0; i--) {
            addRecentTag(context, tags.get(i));
        }
    }
    
    public static List<String> getRecentTags(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String recentTagsStr = prefs.getString(KEY_RECENT_TAGS, "");
        
        if (recentTagsStr.isEmpty()) {
            return new ArrayList<>();
        }
        
        return Arrays.asList(recentTagsStr.split(TAG_SEPARATOR));
    }
} 