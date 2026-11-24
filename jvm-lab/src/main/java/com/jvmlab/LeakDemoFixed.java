package com.jvmlab;

import java.util.concurrent.atomic.AtomicLong;

/**
 * LeakDemo 的修复版：同样的业务流量，但任务处理完不留引用，只累计统计数字。
 *
 * 用文档里同样的 jmap -histo:live 观察，byte[] / Task 的数量应稳定不再增长 ——
 * 这就是「没有泄漏」的对照样本，用来验证修复闭环。
 */
public class LeakDemoFixed {

    /** 修复思路：只留 8 字节的计数，不留 2KB 的对象引用 */
    private static final AtomicLong FINISHED_COUNT = new AtomicLong();
    private static final AtomicLong FINISHED_BYTES = new AtomicLong();

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

        System.out.println("LeakDemoFixed 已启动（修复版，不应泄漏）。jps -l 找 pid 后可用 jmap -histo:live 验证。");
        long start = System.currentTimeMillis();
        int n = 0;
        while (true) {
            process(new Task("task-" + n, payloadBytes));
            n++;
            if (n % 50 == 0) {
                long seconds = (System.currentTimeMillis() - start) / 1000;
                System.out.printf("[%3ds] 已处理 %d 个任务，累计 %d MB（均为瞬时流量，堆内存应保持稳定）%n",
                        seconds, n, FINISHED_BYTES.get() / 1024 / 1024);
            }
            Thread.sleep(intervalMs);
        }
    }

    private static void process(Task task) {
        // ...假装对 task.payload 做了真实处理...
        FINISHED_COUNT.incrementAndGet();
        FINISHED_BYTES.addAndGet(task.payload.length);
    }
}
