package person.notfresh.readingshare.ui.tag;

import android.content.Context;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import person.notfresh.readingshare.db.DbConnection;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.ui.Dropdown;

/**
 * 「选标签」下拉 —— 在通用 {@link Dropdown} 之上加一层标签语义。
 *
 * <p>为什么单独一层:{@link Dropdown} 是通用底座(选启动页/主题/数字都用它),
 * 不认识标签;而「标签列表怎么取、要不要显示链接数、要不要排除某个标签」
 * 这三件事是标签特有的,散在调用方就会各处重复(阅读页数据来源、合并标签…)。</p>
 *
 * <p>用法:</p>
 * <pre>{@code
 *   TagSelectDropdown dropdown = TagSelectDropdown
 *           .attach(ctx, container)
 *           .setHint(R.string.xxx);
 *   dropdown.loadTags(excludeTagName);   // 拉标签列表,选项 = 「标签名 (链接数)」
 *   dropdown.selectedTag();              // 当前选中的标签名
 * }</pre>
 */
public final class TagSelectDropdown {

    /** 一个候选标签:名字 + 链接数。 */
    public static final class TagOption {
        public final String name;
        public final int count;

        TagOption(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }

    private final Context ctx;
    private final Dropdown dropdown;
    /** 候选标签(与 dropdown 的选项同序)。 */
    private final List<TagOption> options = new ArrayList<>();

    private TagSelectDropdown(Context ctx, Dropdown dropdown) {
        this.ctx = ctx.getApplicationContext();
        this.dropdown = dropdown;
    }

    /**
     * 在容器末尾挂一个「选标签」下拉。
     *
     * @param ctx       上下文
     * @param container 宿主容器
     */
    public static TagSelectDropdown attach(Context ctx, ViewGroup container) {
        return new TagSelectDropdown(ctx, Dropdown.attach(ctx, container));
    }

    /** 设置下拉框的提示文字(走底层 Dropdown)。 */
    public TagSelectDropdown setHint(int hintRes) {
        dropdown.setHint(hintRes);
        return this;
    }

    /**
     * 拉取所有标签作为候选项,选项显示为「标签名 (链接数)」。
     *
     * @param excludeTagName 要排除的标签名(合并场景排除源标签自己);传 null 不排除
     */
    public void loadTags(String excludeTagName) {
        options.clear();
        try {
            LinkDao dao = new LinkDao(DbConnection.get(ctx).writable());
            Map<String, Integer> all = dao.getTagsWithCount();
            for (Map.Entry<String, Integer> entry : all.entrySet()) {
                if (excludeTagName != null && excludeTagName.equals(entry.getKey())) {
                    continue;
                }
                options.add(new TagOption(entry.getKey(), entry.getValue()));
            }
        } catch (Exception e) {
            options.clear();
        }
        List<String> labels = new ArrayList<>(options.size());
        for (TagOption option : options) {
            labels.add(option.name + " (" + option.count + ")");
        }
        dropdown.setOptions(labels, 0);
    }

    /** 当前选中的标签名;没有候选时返回 null。 */
    public String selectedTag() {
        int index = selectedIndex();
        if (index < 0) {
            return null;
        }
        return options.get(index).name;
    }

    /** 当前选中标签的链接数;没有候选时返回 0。 */
    public int selectedCount() {
        int index = selectedIndex();
        if (index < 0) {
            return 0;
        }
        return options.get(index).count;
    }

    /** 候选标签是否为空(没有别的标签可选)。 */
    public boolean isEmpty() {
        return options.isEmpty();
    }

    /** 候选标签列表(只读)。 */
    public List<TagOption> options() {
        return Collections.unmodifiableList(options);
    }

    private int selectedIndex() {
        if (options.isEmpty()) {
            return -1;
        }
        int index = dropdown.selectedIndex();
        if (index < 0 || index >= options.size()) {
            return -1;
        }
        return index;
    }
}
