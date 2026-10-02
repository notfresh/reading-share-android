package person.notfresh.readingshare.eventlog;

/**
 * 把一条事件折叠回实体表（"LWW 折叠"——PROTOCOL §5.4）。EventLogClient.pull
 * 拉到事件后调 {@link #apply(EventRecord)}，由实现决定如何把事件折到
 * 业务 DAO（例如 link 表）。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>接口在 eventlog 包，与 {@link EventLogClient} 同包，避免循环依赖。</li>
 *   <li>实现类在自己负责的 link 业务包（{@code links/}）里 — 依赖具体 DAO，
 *       反向依赖被切断。</li>
 *   <li>不提供默认 noop 实现：避免"加了抽象但没人用"的单向门 — 调用方必须
 *       显式注入（参见 SEfirst 表面积 ∝ 价值）。</li>
 * </ul>
 */
public interface LinkApplier {

    /**
     * 把一条事件折叠到对应的实体表。调用方保证：同 {@code (topic, device_id,
     * entity_id)} 的事件按 {@code process_time} 升序到达。
     *
     * @param event 待折叠的事件；非 {@code null}，且 {@code topic/entity_id/action}
     *              字段已通过 schema 校验
     */
    void apply(EventRecord event);
}
