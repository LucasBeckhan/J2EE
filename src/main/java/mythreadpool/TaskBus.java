package mythreadpool;

import java.util.concurrent.atomic.AtomicLong;

/**
 * M5：任务总线 —— 任务的"发布/分发"统一入口，内部持有 MyThreadPool。
 *
 * 工业对照（写完 M5 再去读）：
 *   退化版 → RocketMQ PullMessageService：单线程 take 循环，消息版 task-bus，约百行
 *   完全体 → Kafka RecordAccumulator：攒批 + BufferPool 背压
 *   巅峰版 → Disruptor：环形缓冲 + WaitStrategy
 *
 * 守恒断言：提交的任务数必须 == 执行的任务数（不丢、不重）。
 * 和 Kafka 的 offset 提交语义（at-least-once）是同一个思想 ——
 * 先保证"不丢"，再谈"不重"。
 */
public class TaskBus {

    private final MyThreadPool pool;

    /** 守恒断言的两个计数器：会被多个生产者线程并发累加，必须用原子类 */
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong executed = new AtomicLong();

    public TaskBus(MyThreadPool pool) {
        this.pool = pool;
    }

    /** 发布任务：总线计数后交给线程池执行 */
    public void publish(Runnable task) {
        // TODO(M5): submitted +1 → 把 task 包一层（执行完 executed +1）→ pool.execute(...)
        //           思考：包装层里计数要不要放 finally？任务抛异常算不算"已执行"？
        throw new UnsupportedOperationException("TODO: M5");
    }

    /** 守恒断言：等到执行追平提交，校验 submitted == executed */
    public void assertConservation() throws InterruptedException {
        // TODO(M5): 怎么判定"追平"？轮询计数，或 pool.shutdown() 等 worker 全部退出后再比
        //           断言失败 = 总线丢了任务或重复执行 —— 这正是 offset 语义要解决的问题
        throw new UnsupportedOperationException("TODO: M5");
    }

    public long getSubmitted() {
        return submitted.get();
    }

    public long getExecuted() {
        return executed.get();
    }
}
