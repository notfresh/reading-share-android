package person.notfresh.readingshare.reader;

import android.content.Context;

import java.util.LinkedHashMap;
import java.util.Map;

import person.notfresh.readingshare.model.ReaderSourceSpec;
import person.notfresh.readingshare.core.reader.ReaderSource;

/**
 * 阅读来源注册表（唯一入口）。
 *
 * <p>扩展点：新增一种来源 = 写一个 {@link ReaderSource} 实现 + 调一次
 * {@link #register(String, Factory)}，不改任何现有调用方（对扩展开放）。
 * 未知类型一律降级为"全部链接"，配置坏了也不让阅读页白屏。</p>
 */
public final class ReaderSources {

    /** 来源工厂：把"配置"翻译成"可查询的来源"。 */
    public interface Factory {
        ReaderSource create(Context ctx, ReaderSourceSpec spec);
    }

    private static final Map<String, Factory> FACTORIES = new LinkedHashMap<>();

    static {
        register(ReaderSourceSpec.TYPE_ALL, (ctx, spec) -> new AllLinksSource(ctx));
        register(ReaderSourceSpec.TYPE_PINNED, (ctx, spec) -> new PinnedLinksSource(ctx));
        register(ReaderSourceSpec.TYPE_TAG, (ctx, spec) -> new TagSource(ctx, spec.getParam()));
        register(ReaderSourceSpec.TYPE_SUBJECT, (ctx, spec) -> new SubjectSource(ctx, parseSubjectId(spec.getParam())));
    }

    private ReaderSources() {
        // utility class
    }

    public static void register(String type, Factory factory) {
        if (type == null || type.isEmpty() || factory == null) return;
        FACTORIES.put(type, factory);
    }

    public static ReaderSource create(Context ctx, ReaderSourceSpec spec) {
        ReaderSourceSpec safe = spec == null ? ReaderSourceSpec.all() : spec;
        Factory factory = FACTORIES.get(safe.getType());
        if (factory == null) factory = FACTORIES.get(ReaderSourceSpec.TYPE_ALL);
        return factory.create(ctx.getApplicationContext(), safe);
    }

    /** 可用的来源类型（设置页下拉用），顺序即展示顺序。 */
    public static String[] knownTypes() {
        return FACTORIES.keySet().toArray(new String[0]);
    }

    private static long parseSubjectId(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (Exception e) {
            return -1L;
        }
    }
}
