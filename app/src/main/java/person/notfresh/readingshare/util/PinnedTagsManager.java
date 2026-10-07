package person.notfresh.readingshare.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 置顶 tag 的持久化:SharedPreferences `pinned_tags` (Set<String>).
 *
 * 铁律:最多 3 个。
 * - {@link #add(String)} 超过 3 个直接返回 false,由 UI 弹 toast。
 * - {@link #setPinnedTags(Set)} 接受任意大小,内部截断到前 3 个(防止越界写入)。
 * - 顺序由 LinkedHashSet 保留(调用方传入的顺序就是持久化的顺序,UI 里"置顶区"展示按此序)。
 *
 * 读写入口唯一,不在业务代码里散落 SharedPreferences 字符串。
 */
public final class PinnedTagsManager {
    /** 写死的上限。 */
    public static final int MAX_PINNED = 3;

    private static final String PREFS_NAME = "pinned_tags_prefs";
    private static final String KEY = "pinned_tags";

    private final SharedPreferences prefs;

    public PinnedTagsManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** 当前置顶 tag 列表(按持久化顺序)。永不为 null。 */
    public Set<String> getPinnedTags() {
        Set<String> raw = prefs.getStringSet(KEY, Collections.emptySet());
        return raw == null ? new LinkedHashSet<>() : new LinkedHashSet<>(raw);
    }

    /**
     * 整体替换。超 MAX_PINNED 时只保留前 N 个。
     * 写完立刻提交。
     */
    public void setPinnedTags(Set<String> tags) {
        LinkedHashSet<String> trimmed = new LinkedHashSet<>();
        if (tags != null) {
            for (String t : tags) {
                if (t != null && !t.isEmpty()) {
                    trimmed.add(t);
                    if (trimmed.size() >= MAX_PINNED) break;
                }
            }
        }
        prefs.edit().putStringSet(KEY, trimmed).apply();
    }

    /** 已置顶 ? */
    public boolean isPinned(String tag) {
        return getPinnedTags().contains(tag);
    }

    /**
     * 追加一个置顶 tag。返回 false 表示已达上限,UI 应 toast 提示。
     */
    public boolean add(String tag) {
        if (tag == null || tag.isEmpty()) return false;
        LinkedHashSet<String> current = new LinkedHashSet<>(getPinnedTags());
        if (current.contains(tag)) {
            // 已存在 → no-op (视为成功)
            return true;
        }
        if (current.size() >= MAX_PINNED) {
            return false;
        }
        current.add(tag);
        setPinnedTags(current);
        return true;
    }

    /** 移除一个置顶 tag。不存在 → 已置顶的没变化 → 等价 no-op。 */
    public void remove(String tag) {
        if (tag == null || tag.isEmpty()) return;
        LinkedHashSet<String> current = new LinkedHashSet<>(getPinnedTags());
        if (current.remove(tag)) {
            setPinnedTags(current);
        }
    }
}