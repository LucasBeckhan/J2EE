package com.jvmlab;

import java.util.ArrayList;
import java.util.List;

/**
 * 教学用「故意内存泄漏」演示程序（配套 doc/内存泄漏排查实操-jmap+MAT.md，勿在生产代码模仿）。
 *
 * 剧情设定：一个任务处理器，每处理完一个任务，就把它塞进静态的「已完结任务」列表，
 * 说是留着以后查记录，但从来没有任何代码清理这个列表 —— 最经典的静态集合泄漏。
 *
 * 泄漏速度默认约 200 KB/s（每任务 2KB × 每秒约 100 个），配 -Xmx64m 大约 5 分钟 OOM，
 * 正好留出时间窗口让你用 jmap 观察、抓堆。
 *
 * 运行方式（见文档）：
 *   java -Xmx64m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=dumps com.jvmlab.LeakDemo
 * 可选参数：args[0]=每次泄漏字节数(默认 2048)，args[1]=间隔毫秒(默认 10)。
 * 想快进看 OOM：java -Xmx64m com.jvmlab.LeakDemo 8192 2
 */
public class LeakDemo {

    /** ★泄漏点：static 集合只进不出 —— 被它引用的对象永远无法被 GC 回收 */
    private static final List<Task> FINISHED_TASKS = new ArrayList<>();

    /** 任务对象：自带 2KB payload，让它在 MAT 的 Histogram 里「大到肉眼可见」 */
    static class Task {
        final String id;
        final byte[] payload;

        Task(String id, int payloadBytes) {
            this.id = id;
            this.payload = new byte[payloadBytes];
        }
    }

    public static void main(String[] args) throws Exception {
        int payloadBytes = args.length > 0 ? Integer.parseInt(args[0]) : 2048;
        long intervalMs = args.length > 1 ? Long.parseLong(args[1]) : 10;

        System.out.println("LeakDemo 已启动。另开一个终端，执行 jps -l 找到本进程 pid。");
        System.out.println("泄漏参数：每个任务 " + payloadBytes + " B，每 " + intervalMs + " ms 一个。");

        long start = System.currentTimeMillis();
        int n = 0;
        while (true) {
            FINISHED_TASKS.add(new Task("task-" + n, payloadBytes));
            n++;
            if (n % 50 == 0) {
                long seconds = (System.currentTimeMillis() - start) / 1000;
                long leakedMb = (long) FINISHED_TASKS.size() * payloadBytes / 1024 / 1024;
                System.out.printf("[%3ds] 已处理 %d 个任务，FINISHED_TASKS.size=%d，payload 累计约 %d MB%n",
                        seconds, n, FINISHED_TASKS.size(), leakedMb);
            }
            Thread.sleep(intervalMs);
        }
    }
}
