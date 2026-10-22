package person.notfresh.readingshare.core.reader;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 阅读队列（内核，纯 Java，可单测）：维护一个有界窗口 + 当前游标，
 * 提供 next / previous / random 三种移动，边界处向 {@link ReaderSource} 补页。
 *
 * <p>环形语义：末尾的"下一条"回窗口第一条，开头的"上一条"跳到<b>已加载窗口</b>
 * 的最后一条。也就是说环只在已加载窗口内成立 —— 接口拿不到总条数，不做跨窗口远端跳转。</p>
 *
 * <p>为什么有窗口上限：来源可能是全库链接，用户狂翻不能无限涨内存；
 * 也不允许一次性全量查询（AGENTS.md 第六条）。</p>
 */
public final class ReaderQueue {

    /** 每次向来源取的条数。 */
    public static final int PAGE_SIZE = 50;
    /** 内存中最多保留的条数，超出后从远端裁剪。 */
    public static final int MAX_WINDOW = 200;

    private final ReaderSource source;
    private final Random random;
    private final List<Long> ids = new ArrayList<>();

    private int index = -1;
    private int windowStart = 0;
    private boolean sourceExhausted = false;

    public ReaderQueue(ReaderSource source) {
        this(source, new Random());
    }

    /** 注入 Random 只为可测（random 不重复当前项依赖可控随机源）。 */
    public ReaderQueue(ReaderSource source, Random random) {
        if (source == null) throw new IllegalArgumentException("source == null");
        this.source = source;
        this.random = random == null ? new Random() : random;
    }

    /**
     * 初始化：加载第一页并把游标放在第一条。
     *
     * @return true 表示有内容；false 表示该来源为空
     */
    public boolean load() {
        ids.clear();
        index = -1;
        windowStart = 0;
        sourceExhausted = false;
        appendPage(source.loadPage(0, PAGE_SIZE));
        if (ids.isEmpty()) return false;
        index = 0;
        return true;
    }

    public boolean isEmpty() {
        return ids.isEmpty();
    }

    public int size() {
        return ids.size();
    }

    public int index() {
        return index;
    }

    /** 窗口首条在来源中的偏移（测试与排障用）。 */
    public int windowStart() {
        return windowStart;
    }

    public Long current() {
        return validIndex(index) ? ids.get(index) : null;
    }

    /** 下一条；到末尾自动补页，补不到则环形回第一条。 */
    public Long next() {
        if (ids.isEmpty()) return null;
        if (index + 1 < ids.size()) {
            index++;
            return current();
        }
        if (!sourceExhausted && appendPage(source.loadPage(windowStart + ids.size(), PAGE_SIZE))) {
            index++;
            return current();
        }
        index = 0;
        return current();
    }

    /** 上一条；到开头自动向前补页，补不到则环形跳最后一条。 */
    public Long previous() {
        if (ids.isEmpty()) return null;
        if (index > 0) {
            index--;
            return current();
        }
        if (windowStart > 0) {
            int offset = Math.max(0, windowStart - PAGE_SIZE);
            List<Long> page = source.loadPage(offset, windowStart - offset);
            if (page != null && !page.isEmpty()) {
                ids.addAll(0, page);
                windowStart -= page.size();
                index = page.size() - 1;
                trimTail();
                return current();
            }
        }
        index = ids.size() - 1;
        return current();
    }

    /** 随机一条，保证不等于当前项（窗口只有一条时返回它自己）。 */
    public Long random() {
        if (ids.isEmpty()) return null;
        if (ids.size() == 1) return current();
        int target;
        do {
            target = random.nextInt(ids.size());
        } while (target == index);
        index = target;
        return current();
    }

    private boolean appendPage(List<Long> page) {
        if (page == null || page.isEmpty()) {
            sourceExhausted = true;
            return false;
        }
        if (page.size() < PAGE_SIZE) sourceExhausted = true;
        ids.addAll(page);
        trimHead();
        return true;
    }

    /** 向后翻多了：丢掉窗口头部，游标跟着左移，保持指向同一条。 */
    private void trimHead() {
        int overshoot = ids.size() - MAX_WINDOW;
        if (overshoot <= 0) return;
        ids.subList(0, overshoot).clear();
        windowStart += overshoot;
        index -= overshoot;
        if (index < 0) index = 0;
    }

    /** 向前补页后窗口超限：丢掉尾部（不影响游标所在的头部）。 */
    private void trimTail() {
        int overshoot = ids.size() - MAX_WINDOW;
        if (overshoot <= 0) return;
        ids.subList(ids.size() - overshoot, ids.size()).clear();
    }

    private boolean validIndex(int i) {
        return i >= 0 && i < ids.size();
    }
}
