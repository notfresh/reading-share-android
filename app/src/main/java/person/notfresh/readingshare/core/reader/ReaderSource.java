package person.notfresh.readingshare.core.reader;

import java.util.List;

/**
 * 阅读队列的数据来源（内核接口，纯 Java，不依赖 Android）。
 *
 * <p>只回答一个问题：按偏移给我一页有序 linkId。为什么不是"给我全部"——
 * 项目有 3000+ 链接，禁止一次性全量加载（AGENTS.md 第六条）。</p>
 *
 * <p>扩展方式：新增来源 = 新增一个实现类 + 在
 * {@link person.notfresh.readingshare.reader.ReaderSources#register} 注册一行，
 * 不改任何现有文件（对扩展开放、对修改关闭）。</p>
 */
public interface ReaderSource {

    /** 稳定类型标识，用于持久化与注册表查找（见 ReaderSourceSpec.TYPE_*）。 */
    String type();

    /**
     * 取 [offset, offset + limit) 区间的 linkId，顺序即队列顺序。
     *
     * @return 空列表表示已经没有更多；短页（size < limit）表示到达末尾
     */
    List<Long> loadPage(int offset, int limit);
}
