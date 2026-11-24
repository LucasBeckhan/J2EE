package mythreadpool;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 手写线程池 —— 四个里程碑推进（与 minispring 的"三步走"同节奏）：
 * M1 单线程池：队列 + 1 个常驻 Worker，循环 take() 再 run()
 * M2 固定线程池：开 corePoolSize 个 worker，线程命名，run() 要 try-catch 保护
 * M3 完整参数：core/max/有界队列/拒绝策略，execute() 三段判断（对齐 JDK）
 * M4 生命周期：volatile running + shutdown() / shutdownNow()
 *
 * 源码阅读路线（先写后读，每个里程碑写完再对照）：
 *   正文   JDK ThreadPoolExecutor        —— M3 写完后逐行对照 execute() 的三段 if
 *   变体一 Tomcat util.threads.TaskQueue —— "先建线程到 max 再入队"的反向改造
 *   变体二 Dubbo AbortPolicyWithReport    —— 生产环境拒绝时如何自动留案发现场（dump jstack）
 *   关闭   Netty shutdownGracefully       —— quiet period：多久没新任务才算真关了
 */
public class MyThreadPool {

    /** M1/M2 只用 corePoolSize；maximumPoolSize、有界队列、拒绝策略在 M3 解锁 */
    private final int corePoolSize;
    private final int maximumPoolSize;
    private final BlockingQueue<Runnable> workQueue;

    /** M4：main 线程写、worker 线程读 —— 可见性靠 volatile */
    private volatile boolean running = true;

    public MyThreadPool(int corePoolSize) {
        this(corePoolSize, corePoolSize, new LinkedBlockingQueue<>());
    }

    public MyThreadPool(int corePoolSize, int maximumPoolSize, BlockingQueue<Runnable> workQueue) {
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.workQueue = workQueue;

        // M1: 启动 1 个常驻 Worker —— 死循环 take() -> run()，队列空就阻塞，不空转烧 CPU
        Thread worker = new Thread(this::workerLoop, "my-pool-1");
        worker.start();
        // TODO(M2): 改为启动 corePoolSize 个，编号命名，方便 jstack 观察
    }

    /** M1：Worker 主体 —— 取一个任务就地执行；注意是 run() 不是 start()，任务跑在 worker 线程上 */
    private void workerLoop() {
        try {
            while (true) {
                Runnable task = workQueue.take(); // 阻塞等待：没有新任务就挂起
                task.run();                       // 任务自己抛异常会带崩 worker，M2 再加 try-catch 保护
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // M4 shutdownNow() 中断 worker 时才会走到这里
        }
    }

    /**
     * 提交任务。M3 写完后的目标语义（对齐 JDK 三段式）：
     *   1. 运行线程 < core → 新建线程直接执行
     *   2. 否则            → 入队（任务在总线上排队）
     *   3. 队满且 < max    → 再新建线程
     *   4. 还是不行        → 拒绝（至少实现 AbortPolicy / CallerRunsPolicy）
     */
    public void execute(Runnable task) {
        // M1: 最简版 —— 入队即提交，剩下的交给唯一的 worker 循环取走执行
        try {
            workQueue.put(task); // 队列有界且满时会阻塞生产者，M3 在这里升级为拒绝策略
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("提交任务时被中断", e);
        }
    }

    /** TODO(M4): 拒收新任务；队列跑完后 worker 自然退出 */
    public void shutdown() {
        throw new UnsupportedOperationException("TODO: M4");
    }

    /** TODO(M4): 拒收新任务；清空并返回队列里未执行的任务；interrupt 所有 worker */
    public List<Runnable> shutdownNow() {
        throw new UnsupportedOperationException("TODO: M4");
    }

    /** 给 TaskBus / PoolDemo 观察队列积压用 */
    public int getQueueSize() {
        return workQueue.size();
    }
}
