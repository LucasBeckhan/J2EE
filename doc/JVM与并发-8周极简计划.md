# JVM 与并发 · 8 周极简计划

> 每天 1 小时,每周只做 3 件事:**读一个八股主题 → 看一个类的源码 → 跑一个实验**。
> 规则:实验必须亲手跑,八股结论要能说"我验证过"。

## 资料(只列 3 个)

- 八股:[JavaGuide](https://github.com/SnailClimb/JavaGuide) 对应章节查漏补缺
- JVM 书:《深入理解 Java 虚拟机》第 3 版(只读第 2、3、12、13 章)
- 并发书:《Java 并发编程的艺术》(对应周翻对应章)

环境:JDK 8 + JDK 17 各装一个;IDEA 能断点进 JDK 源码即可。工具用到再装(JOL、Arthas)。

---

## 第 1–4 周:JVM

| 周 | 八股主题 | 源码/工具 | 实验(1 个就够) |
|----|----------|-----------|------------------|
| W1 | 运行时数据区、对象创建与内存布局 | JOL | 用 JOL 打印对象头;写出 4 种 OOM/栈溢出(堆、元空间、栈、无法终止线程) |
| W2 | GC 算法、CMS/G1/ZGC 区别、Full GC 触发条件 | 无(读 JEP 代替) | 开 `-Xlog:gc*` 跑一段分配密集代码,日志丢 GCeasy,对比 `-XX:+UseParallelGC` vs `UseG1GC` 停顿 |
| W3 | 类加载 5 阶段、双亲委派及 3 个破坏案例 | javap | 手写 ClassLoader 加载同一个类两次,证明 instanceof 不互认;javap 看 synchronized 字节码 |
| W4 | JMM、happens-before、volatile、synchronized 锁升级 | 无 | ①`while(!flag)` 可见性实验 ②JDK8 下用 JOL 观察偏向→轻量→重量的 Mark Word 变化 ③埋死循环,走一遍 top→jstack 排查 CPU 100% |

**JVM 毕业标准**:能白板画内存区域图;能独立排查一次人为制造的 CPU 100% 或 OOM。

---

## 第 5–8 周:并发

| 周 | 八股主题 | 源码(debug 跟读) | 实验 |
|----|----------|--------------------|------|
| W5 | 线程 6 状态、中断机制、ThreadLocal 原理与泄漏 | ThreadLocal、Thread | 线程池 + ThreadLocal 不 remove,复现"下一个任务读到上个任务的值" |
| W6 | CAS、ABA、AtomicLong vs LongAdder | AtomicInteger.incrementAndGet、LongAdder.add | 压测 1/16/64 线程下 AtomicLong vs LongAdder vs synchronized 计数,画折线图 |
| W7 | AQS 结构、公平/非公平锁、CountDownLatch/Semaphore | AQS.acquire/release → ReentrantLock → CountDownLatch(一页,逐行) | 继承 AQS 手写一把可重入锁;用 CountDownLatch 写"多线程分片处理后汇总" |
| W8 | 线程池 7 参数、execute 流程、拒绝策略、为什么禁用 Executors | ThreadPoolExecutor.execute / runWorker / getTask | 手写迷你线程池(队列 + Worker + 优雅关闭);打印日志确认"先入队后扩 max" |

**并发毕业标准**:白板讲清 AQS acquire 流程;手写锁和线程池代码可运行。

---

## 收尾(第 9 周起,碎片时间)

- 自测 20 题:JVM 10 题(内存区域 / GC 触发 / G1 原理 / 双亲委派 / volatile / happens-before / 锁升级 / DCL / 排查思路 / 元空间)+ 并发 10 题(CAS / ABA / ThreadLocal 泄漏 / AQS / 公平锁 / synchronized vs Lock / 线程池流程 / 线程复用原理 / CHM put 流程 / 为什么禁 Executors)
- 答不上来的题,回到对应周补实验

## 大数据加分(可选,面试前一周做)

把学过的东西挂到场景上:Kafka 快 = 顺序写 + pagecache + 零拷贝;Flink 不怕 GC = 堆外托管内存;Shuffle = 堆内存不够溢写磁盘。每条能讲 1 分钟即可。
