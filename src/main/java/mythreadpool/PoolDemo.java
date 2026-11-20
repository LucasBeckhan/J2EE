package mythreadpool;

/**
 * 验证入口（与 minispring.Main 同风格）：
 * 每完成一个里程碑就跑一次，看输出对照"完成标志"。
 */
public class PoolDemo {

    public static void main(String[] args) throws InterruptedException {
        m1();
        // m2();  // M2 写完后放开
        // m5();  // M5 写完后放开
    }

    /** M1 完成标志：5 个任务全部由 my-pool-1 一个线程顺序执行 */
    private static void m1() throws InterruptedException {
        MyThreadPool pool = new MyThreadPool(1);
        for (int i = 1; i <= 5; i++) {
            final int id = i;
            pool.execute(() -> System.out.println(Thread.currentThread().getName() + " 执行任务 " + id));
        }
        Thread.sleep(500); // 偷懒等执行完；M4 写完 shutdown() 后换成优雅等待
    }

    /** M2 完成标志：10 个 100ms 任务由 3 个 worker 并发，总耗时约 400ms（4 轮），输出三个线程名交错 */
    private static void m2() throws InterruptedException {
        // TODO(M2): new MyThreadPool(3)，投 10 个 sleep(100) 任务，记录起止耗时验证并发度
    }

    /** M5 完成标志：4 个生产者线程共 publish 1000 个任务，assertConservation() 不抛断言错误 */
    private static void m5() throws InterruptedException {
        // TODO(M5): 4 个 Thread 各 publish 250 个任务 → join → bus.assertConservation()
    }
}
