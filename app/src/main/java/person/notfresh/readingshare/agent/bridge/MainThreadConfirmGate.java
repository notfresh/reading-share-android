package person.notfresh.readingshare.agent.bridge;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 确认门的**主线程调度实现** —— 把后台线程的确认请求搬到主线程弹窗,
 * 并阻塞等待用户的选择。
 *
 * <p><b>线程模型</b>:agent 内核跑在 Python 线程(后台),但弹窗必须在主线程。
 * 所以:</p>
 * <ol>
 *   <li>后台线程调 {@link #requestConfirm} → 把请求 post 到主线程</li>
 *   <li>主线程弹窗 → 用户点按钮 → 结果放进队列</li>
 *   <li>后台线程从队列取结果(带超时)→ 返回给 Python</li>
 * </ol>
 *
 * <p><b>为什么用队列而不是 CountDownLatch</b>:队列自带超时语义
 * ({@link BlockingQueue#poll} 带 timeout),超时后不能无限期挂住后台线程 ——
 * 用户不点确认时,agent 不能永远卡在那儿。</p>
 *
 * <p><b>死锁防护</b>:
 * <ul>
 *   <li>只在**有 Looper 的线程**调用(通常是主线程)时,才允许直接同步等</li>
 *   <li>主线程调进来 → 不 post 给自己(否则死锁),直接同步执行弹窗</li>
 *   <li>后台线程调进来 → post 到主线程 + 有界等待</li>
 * </ul>
 *
 * <p><b>超时</b>:默认 60 秒。超时按"拒绝"处理 —— 宁可不做,
 * 也不要替用户做决定。</p>
 */
public final class MainThreadConfirmGate implements AgentConfirmGate {

    /** 确认等待超时(秒)。超时按拒绝处理。 */
    private static final long CONFIRM_TIMEOUT_SECONDS = 60;

    /** 弹窗的实际构造委托 —— 由 UI 层提供(Fragment/Activity)。 */
    public interface ConfirmDialogPresenter {
        /**
         * 在主线程弹确认框。
         *
         * @param toolName  工具名
         * @param argsJson  参数 JSON
         * @param onResult  回调 —— 用户选择后调用。传入 null = 同意,非空 = 拒绝原因
         */
        void present(String toolName, String argsJson, ConfirmResultCallback onResult);
    }

    /** 确认结果的回调接口。 */
    public interface ConfirmResultCallback {
        /** @param rejectReason null = 同意;非空 = 拒绝原因 */
        void onResult(String rejectReason);
    }

    private final ConfirmDialogPresenter presenter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public MainThreadConfirmGate(ConfirmDialogPresenter presenter) {
        this.presenter = presenter;
    }

    @Override
    public String requestConfirm(String toolName, String argsJson) {
        // 已经在主线程 → 直接弹,不 post(否则死锁)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            final AtomicReference<String> result = new AtomicReference<>();
            final CountDownLatch latch = new CountDownLatch(1);
            presenter.present(toolName, argsJson, rejectReason -> {
                result.set(rejectReason);
                latch.countDown();
            });
            try {
                if (!latch.await(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    return "用户未在 " + CONFIRM_TIMEOUT_SECONDS + " 秒内确认,已取消";
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "确认等待被中断,已取消";
            }
            return result.get();
        }

        // 后台线程 → post 到主线程弹窗,这边有界等待
        final BlockingQueue<String> queue = new ArrayBlockingQueue<>(1);
        mainHandler.post(() -> presenter.present(toolName, argsJson, rejectReason -> {
            queue.offer(rejectReason == null ? "" : rejectReason);
        }));
        try {
            String result = queue.poll(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (result == null) {
                // 超时 = 拒绝 —— 宁可不做,也不替用户决定
                return "用户未在 " + CONFIRM_TIMEOUT_SECONDS + " 秒内确认,已取消";
            }
            if (result.isEmpty()) {
                return null;  // 同意
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "确认等待被中断,已取消";
        }
    }
}
