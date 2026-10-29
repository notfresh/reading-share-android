package person.notfresh.readingshare.agent.bridge;

/**
 * 写操作确认门 —— agent 的写操作必须经用户同意。
 *
 * <p>**为什么是接口而不是直接弹窗**:agent 内核跑在 Python 线程里,
 * 弹窗必须在主线程。实现方(Activity/Fragment)自己管线程切换与弹窗时机,
 * 本接口只表达"我需要用户对某个操作表个态"。依赖倒置,
 * agent 桥接层不认识 Android UI。</p>
 *
 * <p>语义与 minimal-agent-v2 的 guard 检查点一致
 * (见 v2 tool_runner.py 的 {@code if self.guard is not None:}):</p>
 * <ul>
 *   <li>返回 {@code null} → 放行,工具执行</li>
 *   <li>返回非空字符串 → 拒绝,字符串回灌给 LLM 当作 Observation,
 *       让它换方案而不是卡死</li>
 * </ul>
 */
public interface AgentConfirmGate {

    /**
     * 请求用户确认一次写操作。
     *
     * <p>实现方**必须阻塞**到用户做出选择(同意 / 拒绝)再返回 ——
     * Python 侧的 guard 是同步调用。</p>
     *
     * @param toolName  工具名(如 add_tag_to_link)
     * @param argsJson  参数 JSON 字符串(用于弹窗里展示"要干什么")
     * @return {@code null} = 用户同意;非空 = 拒绝原因(会回灌给 LLM)
     */
    String requestConfirm(String toolName, String argsJson);
}
