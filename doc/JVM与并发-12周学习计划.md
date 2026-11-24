# JVM 与并发 · 12 周学习计划(大数据方向定制版)

> 设计原则:**八股文不是"读",是"自测清单";源码不是"通读",是"带着问题定点爆破";实验不是"跑一遍",是"亲手复现故障再修好它"。**
> 三线并行,每周一个主题闭环:八股文清单(建立知识框架)→ 源码任务(理解实现)→ 实验(亲手验证)→ 输出(费曼式复述)。

---

## 0. 验收标准(什么叫做"学会了")

全部达成才算毕业:

| # | 能力项 | 验收方式 |
|---|--------|----------|
| 1 | 能在白板上画出 JVM 运行时数据区、对象内存布局、GC 分代模型 | 不看资料画图并讲解 10 分钟 |
| 2 | 遇到任意 OOM/CPU 100%/频繁 Full GC,能独立完成排查 | 在故意埋雷的程序上走通完整排查链路(top→jstack / jstat→jmap→MAT) |
| 3 | 能画出 AQS 的 CLH 队列结构,讲清 acquire/release 全流程 | 白板画图 + 讲给不懂的人听懂 |
| 4 | 能手写:迷你线程池、基于 AQS 的锁、双 Condition 有界队列 | 代码可运行,有单元测试 |
| 5 | 能说清 volatile/synchronized/CAS 的底层实现和适用场景 | 回答附录 50 题中对应题目不卡壳 |
| 6 | 能把 JVM/并发知识落到大数据场景 | 能解释 Flink 托管内存、Shuffle 溢写、Kafka 零拷贝背后的 JVM 原理 |
| 7 | 完成综合大作业(见 W12) | 程序可运行,附 JMH 报告 + GC 调优前后对比 |

**总时间预算**:约 130–160 小时(工作日每天 1.5–2h,周末每天 3–4h)。时间多可压缩到 8 周,少则拉到 16 周,周结构不变。

---

## 1. 总体架构

```
        ┌─────────────── JVM 线(W1–W5)───────────────┐
        │ 内存区域/对象 → GC/收集器 → 类加载/字节码     │
        │ → JMM/volatile/synchronized → 调优实战       │
        └─────────────────────────────────────────────┘
        ┌─────────────── 并发线(W6–W10)──────────────┐
        │ 线程/ThreadLocal → CAS/原子类 → AQS/锁        │
        │ → 线程池 → 并发容器                          │
        └─────────────────────────────────────────────┘
        ┌─────────────── 融合线(W11–W12)─────────────┐
        │ Netty 内存与线程模型 → 大数据场景应用          │
        │ → 输出/模拟面试/综合大作业                    │
        └─────────────────────────────────────────────┘
```

八股文资料来源(对照我给的每周清单查漏补缺,不要从头刷):
- **JavaGuide**(github.com/SnailClimb/JavaGuide):最主流,按周清单对应章节查
- **CS-Notes**(github.com/CyC2018/CS-Notes):Java 并发 + JVM 两章
- **美团技术团队博客**:搜"Java 线程池""NIO""CAS"几篇经典文,质量远超八股站
- **小林 coding**:图解网络/系统部分顺带复习(大数据岗也考)

---

## 2. 环境准备(第 1 天完成)

| 项 | 说明 |
|----|------|
| **JDK 8**(必装) | 偏向锁、CMS 实验需要;JDK15+ 偏向锁已废弃(JEP 374)、JDK14 移除 CMS |
| **JDK 17 或 21**(主力) | G1 默认、ZGC 生产可用、`-Xlog` 统一日志 |
| IDEA(社区版即可) | debug 源码主力,把 JDK src.zip 关联上,可直接断点进 JDK 源码 |
| Maven | JOL / JMH / Netty 依赖管理 |
| **JOL** `org.openjdk.jol:jol-core:0.17` | 看对象头、锁状态 |
| **JMH** `org.openjdk.jmh` | 正经基准测试,W7 起使用 |
| **Arthas**(阿里开源,下载 arthas-boot.jar) | 线上诊断神器,大数据岗加分项 |
| **MAT**(Eclipse Memory Analyzer) | 堆 dump 分析 |
| VisualVM 或 JConsole | 入门级监控 |
| GCeasy.io / GCViewer | GC 日志可视化 |

> 建议建一个 `jvm-lab` Maven 工程 + 一个 `conc-lab` Maven 工程,每周实验按包分:`w1`、`w2`……所有代码留档,这是你的作品集。

---

## 3. 分周详细计划

### W1 · Java 基础与内存复盘(热身周,量小)

**目标**:把地基裂缝补上,建立"用实验验证八股"的习惯。

**八股清单**
- 8 种基本类型与字节数;包装类缓存(Integer -128~127)
- String 不可变性、字符串常量池、`intern()` 在 JDK6 vs JDK8 的行为差异
- `==` / `equals` / `hashCode` 契约;为什么重写 equals 必须重写 hashCode
- 深拷贝 vs 浅拷贝;序列化(transient、serialVersionUID)
- 异常体系(checked/unchecked、try-with-resources)
- 接口 vs 抽象类、重载 vs 重写(顺带:泛型擦除)

**必答题(自测)**
1. `new String("ab")` 创建了几个对象?
2. JDK8 中 `String s = "a"+"b"; s == "ab"` 结果?换成两个变量拼接呢?为什么?
3. HashMap 的 key 用可变对象会发生什么?

**实验**
1. `Integer i=127,j=127; i==j` vs `128`,再写 `-XX:AutoBoxCacheMax=1000` 观察缓存区间可调
2. JDK8 下验证 `intern()`:先 new 再 intern vs 先字面量,对照《深入理解 JVM》第 3 版 4.3.3 的经典例子
3. `javap -c` 看字符串拼接字节码:JDK8 是 StringBuilder,JDK9+ 是 invokedynamic(StringConcatFactory)——这就是"八股更新要跟上版本"的例子

**输出**:把 3 个实验的结论写成 3 段"我亲手验证过"的笔记(以后面试可以直接说"我做过实验")。

---

### W2 · JVM 内存区域与对象的一生

**目标**:白板上能画内存区域图和对象布局图。

**八股清单**
- 运行时数据区:堆、虚拟机栈、本地方法栈、方法区(永久代→元空间的演变及原因)、程序计数器;哪些共享/哪些私有、哪些会 OOM/StackOverflow
- 栈帧结构:局部变量表、操作数栈、动态链接、返回地址
- 对象创建五步:类加载检查→分配内存(指针碰撞 vs 空闲列表;TLAB 是什么、为什么需要)→零值初始化→设置对象头→执行 `<init>`
- 对象内存布局:对象头(Mark Word 64 位结构表、Klass 指针、数组长度)、实例数据(字段重排序)、对齐填充
- 对象访问定位:句柄 vs 直接指针(HotSpot 用哪个,为什么)
- 逃逸分析、标量替换、栈上分配("对象一定分配在堆上吗"的标准反例)
- `-Xms -Xmx -Xss -Xmn -XX:MetaspaceSize` 等核心参数

**源码任务(轻量)**
- `Thread` 构造器:看 stackSize 参数如何传到 native
- 浏览 `Object` 类全部方法(wait/notify 家族声明为 native)

**实验**
1. **JOL 初体验**:`ClassLayout.parseInstance(obj).toPrintable()` 打印普通对象/数组的对象头;计算一个 `new Object()` 为什么是 16 字节、一个只有 int 字段的类占多少
2. **四种内存错误复现**(每个写独立 main,注释记录参数与现象):
   - 堆 OOM:`-Xmx20m`,List 疯狂 add → `java.lang.OutOfMemoryError: Java heap space`
   - 栈溢出:`-Xss160k` 无限递归 → StackOverflowError
   - 元空间 OOM:`-XX:MaxMetaspaceSize=32m`,用 CGLIB/动态代理(或 Javassist)疯狂生成类
   - 无法停止的线程 OOM(经典):不断 new Thread
3. **逃逸分析验证**:`-XX:+DoEscapeAnalysis`(JDK8 默认开)下跑一个百万次局部对象分配的方法,对比 `-XX:-DoEscapeAnalysis`,用 JMH 或计时观察差距;再用 JFR/jstat 确认 GC 次数变化

**输出**:手绘两张图拍照存档:①运行时数据区全景 ②64 位 HotSpot 开启压缩指针时的对象头 Mark Word 五种状态表。

---

### W3 · GC:算法、收集器与调优参数

**目标**:能说清"一次 Young GC / Full GC 发生了什么",会看 GC 日志。

**八股清单**
- 判活:引用计数(循环引用问题)vs 可达性分析;GC Roots 有哪些(虚拟机栈引用、静态变量、常量、native 引用、同步锁持有的对象……)
- 四种引用:强软弱虚;WeakHashMap/SoftReference 应用场景;`finalize()` 为什么被废弃
- 三大算法:标记-清除(碎片)、标记-复制(空间浪费、为什么新生代用 Eden:S0:S1=8:1:1)、标记-整理
- 分代假说;Minor GC / Major GC / Full GC 的触发条件(Eden 满、老年代不足、元空间不足、System.gc()、担保失败)
- **跨代引用**:卡表(CardTable)+ 写屏障;记忆集(RSet)
- 收集器:Serial / ParNew / Parallel Scavenge(吞吐量优先)/ CMS(四阶段、并发失败 Concurrent Mode Failure、浮动垃圾、碎片)/ **G1**(Region、Humongous 对象、RSet、SATB、可预测停顿模型、Mixed GC)/ ZGC & Shenandoah(染色指针、读屏障、亚毫秒停顿——知道原理层面即可)
- JDK8 默认组合(Parallel Scavenge + Parallel Old)、JDK9+ 默认 G1
- 空间分配担保机制
- GC 日志格式与关键指标(停顿时间、吞吐量、晋升量)

**源码任务**:无(本周纯 JVM 层,源码在 HotSpot C++,不读;读两篇 JEP 代替:JEP 248 G1 成为默认、JEP 333 ZGC)

**实验**
1. **GC 日志分析**(核心实验):
   - JDK17:`java -Xlog:gc*:file=gc.log:time,uptime,level,tags -Xms64m -Xmx64m -XX:+UseG1GC Demo`
   - JDK8:`-XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:gc.log`
   - Demo:循环分配 1MB byte[] 并随机保留一部分。把日志传 GCeasy,读懂:暂停时间、堆变化曲线、晋升
2. **收集器对比**:同一段分配密集型代码分别用 `-XX:+UseParallelGC` / `UseG1GC` / `UseZGC`(JDK17)跑,记录 GC 总暂停时间、最大单次停顿、吞吐量;亲手得出"吞吐优先选 Parallel,延迟优先选 G1/ZGC"的结论
3. **对象晋升观察**:`-XX:MaxTenuringThreshold=1` vs `15`,配合 `-Xlog:gc+age=trace`(JDK11+)或日志中的年龄字段,观察对象何时进老年代
4. **软硬引用验证**:内存吃紧时 SoftReference 何时被回收;WeakHashMap 的 entry 消失时机(手动 System.gc 后检查)
5. **System.gc() 实验**:默认触发 Full GC;加 `-XX:+ExplicitGCInvokesConcurrent` 后行为变化;`-XX:+DisableExplicitGC` 后 DirectByteBuffer 的隐患(为 W5 堆外内存埋伏笔)

**输出**:一张自制的"触发 Full GC 的条件"清单 + 收集器对比数据表(这是面试讲 GC 调优经验的素材)。

---

### W4 · 类加载机制与字节码

**目标**:讲清双亲委派及其破坏;能用 javap 读懂常见语法糖的字节码。

**八股清单**
- 类加载七阶段:加载→验证→准备(静态变量零值)→解析→初始化(`<clinit>`、静态变量真正赋值)→使用→卸载;准备 vs 初始化的区别(`static int a=1` 与 `static final int a=1` 的不同)
- 类初始化的触发时机(主动引用 6 种 vs 被动引用:子类引用父类静态字段、数组定义、常量)
- 双亲委派:三层类加载器(Bootstrap/Ext|Platform/App)、委派的意义(安全+避免重复加载)
- **破坏双亲委派三案例**:JDBC SPI(Thread.currentThread().getContextClassLoader())、Tomcat(每个 webapp 独立加载器,隔离+复用)、OSGi;以及 JDK9 模块化后的变化
- 自定义类加载器:重写 `findClass` vs `loadClass` 的区别
- 字节码:常量池、方法表;`javap -v` 各段含义
- 语法糖:synchronized→monitorenter/monitorexit(与 W5 衔接)、foreach→Iterator、自动装箱、泛型擦除(桥方法)、try-with-resources
- 方法调用:invokestatic/invokevirtual/invokeinterface/invokedynamic;静态分派(重载)vs 动态分派(重写)
- Java Agent / Instrumentation / premain(Arthas、Flink metrics、SkyWalking 的原理基础)

**实验**
1. **javap 读字节码**:对同一段代码分别观察 synchronized 块、foreach、字符串 switch、lambda(invokedynamic + LambdaMetafactory)的字节码
2. **`<clinit>` 执行顺序**:父类/子类静态块、`new` 时的初始化顺序,预测再运行验证(经典八股,亲手跑一遍就再也不会错)
3. **自定义类加载器实现"同类不互认"**:写两个独立的 ClassLoader 加载同一个 class 文件,`instanceof` 为 false;再实现一个文件监听热加载版本(改代码不重启生效)
4. **破坏双亲委派**:写一个类加载器优先自己加载 `java.lang.String`(会抛 SecurityException,理解为什么);再用线程上下文类加载器复现 SPI 模式
5. **写一个最简 Java Agent**:premain 里用 Instrumentation + ASM/Javassist 给方法织入耗时统计——做完这个你会瞬间理解 Arthas 的原理
6. **Arthas 初体验**:对任意运行中程序执行 `dashboard`、`jad`(反编译)、`watch`、`trace`——大数据岗线上排查利器,务必熟练

**输出**:双亲委派 + 三大破坏案例的讲解稿(5 分钟版)。

---

### W5 · JMM、volatile、synchronized 原理 + JVM 排查综合演练

**目标**:happens-before 不再靠背;能独立完成 CPU 100% / 内存泄漏 / 堆外内存三类排查。**本周是 JVM 线的毕业设计。**

**八股清单**
- JMM 抽象:主内存/工作内存、8 种原子操作(了解即可)、三大特性(原子/可见/有序)
- **happens-before 八大规则**(程序顺序、锁、volatile、传递性、线程启动/终止、中断、终结器)——重点理解它是"可见性承诺"而非"时间先后"
- volatile:可见性实现(lock 前缀指令 + 缓存一致性协议 MESI 层面了解)、**内存屏障四规则**(LoadLoad/StoreStore/LoadStore/StoreLoad)、为什么不能保证原子性(i++ 的字节码)、DCL 单例为什么必须加 volatile(半初始化对象 + 重排序)
- as-if-serial 与 happens-before 的关系;final 字段的重排序规则
- synchronized:JVM 层(monitorenter/exit、ObjectMonitor、重量级)与 API 层(锁对象头);**锁升级**:无锁→偏向锁(撤销成本、JDK15 废弃原因)→轻量级锁(CAS 自旋、Lock Record)→重量级锁(膨胀、OS mutex、用户态/内核态切换);锁消除、锁粗化
- synchronized vs ReentrantLock 八股对比表(为 W8 铺垫)
- 伪共享(false sharing)与缓存行(64B)、`@Contended`
- JVM 排查方法论:
  - CPU 100%:`top -Hp <pid>` → 线程号转 16 进制 → `jstack` 定位(或 Arthas `thread -n 3`)
  - 频繁 Full GC:`jstat -gcutil <pid> 1000` → `jmap -histo` → dump → MAT 支配树/泄漏报告
  - OOM 自动取证:`-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=`
  - 堆外内存:`-XX:NativeMemoryTracking=summary` + `jcmd VM.native_memory`;DirectByteBuffer 与 Cleaner(虚引用)机制、`-XX:MaxDirectMemorySize`、为什么 DisableExplicitGC + Netty 会出问题
- JFR(Java Flight Recorder):`jcmd <pid> JFR.start` ——生产环境低开销采集,比 jstack 高级

**源码任务**
- `java.lang.ref.Cleaner`(JDK9+)/ `sun.misc.Cleaner`(JDK8)+ `DirectByteBuffer` 构造与释放路径
- 浏览 `Object.wait/notify` 声明与 `Thread.State` 枚举

**实验(综合演练周,全是排障实战)**
1. **可见性实验**:子线程 `while(!flag){}` 死循环,主线程改 flag 不退出;加 volatile 退出;再用 `-Xint` 解释执行观察"编译器优化才是元凶";顺带证明 `count++` 用 volatile 依然丢更新
2. **DCL 单例**:写出去掉 volatile 的版本,用 javap 看 `new` 的三步字节码,解释重排序窗口
3. **锁升级观察(JDK8)**:`-XX:+UseBiasedLocking -XX:BiasedLockingStartupDelay=0`,用 JOL 依次打印:只有主线程访问(偏向)→ 第二线程交替进入(轻量级,Mark Word 指向 Lock Record)→ 两线程激烈竞争(重量级,Monitor)。截图 Mark Word 变化——这是全网八股最经典的图,你自己做一张
4. **CPU 100% 全流程排查**:埋一个死循环线程,从 top 到 jstack 到 Arthas thread 完整走两遍,写成 runbook
5. **内存泄漏排查**:埋一个 static Map 缓存不设上限的程序,`jmap -dump` → MAT 找支配树 → 定位 GC Root;再用 `-XX:+HeapDumpOnOutOfMemoryError` 验证自动 dump
6. **堆外内存失控**:`-XX:MaxDirectMemorySize=32m -XX:+DisableExplicitGC` 下疯狂 `ByteBuffer.allocateDirect` 且不断开引用 → 观察 OOM: Direct buffer memory;去掉 DisableExplicitGC 或手动调 `((DirectBuffer)buf).cleaner().clean()` 验证解决——**这个实验直接对应 Netty/Kafka/Flink 堆外内存问题**
7. **JFR 采集**:对自己埋雷的程序录 60s,用 JDK Mission Control 打开看火焰图、分配热点、锁竞争

**输出**:①happens-before 讲解稿 ②三份排查 runbook(CPU/堆内/堆外)。**JVM 线到此毕业,对照验收标准 1、2 自评。**

---

### W6 · 线程基础、中断与 ThreadLocal

**目标**:线程状态与中断机制烂熟;ThreadLocal 原理与泄漏能手画。

**八股清单**
- 线程创建方式本质(start vs run、Runnable/Callable/FutureTask、线程池才是正道)
- **6 种线程状态**(NEW/RUNNABLE/BLOCKED/WAITING/TIMED_WAITING/TERMINATED)及全部转换路径;注意 Java 层面 sleep 是 TIMED_WAITING 而非 BLOCKED、synchronized 抢锁才是 BLOCKED、wait/join/park 是 WAITING
- sleep vs wait(锁释放?所属类?唤醒方式?)vs yield vs join 对比表
- **中断机制**:interrupt 只是设标志位;阻塞方法(sleep/wait/join)被中断抛 InterruptedException **并清除标志**;正确响应中断的两种姿势(重新 interrupt 或往上抛);interrupted() 静态方法会清标志(坑)
- 守护线程(setDaemon 时机、JVM 退出条件);shutdown hook
- 线程通信:wait/notify 为什么必须在 synchronized 内;lost wakeup 问题;Condition 预告
- **ThreadLocal**:每个 Thread 持有 ThreadLocalMap;Entry 的 key 是弱引用 value 是强引用 → 泄漏链路;为什么 get/set 时有探测式清理(expungeStaleEntry)但仍必须手动 remove();**线程池场景为什么尤其危险**(线程复用不销毁);hash 冲突用开放寻址而非链表(对比 HashMap)
- InheritableThreadLocal 及其在线程池下失效的问题 → 阿里 TransmittableThreadLocal(TTL)——大数据平台传递 trace/用户上下文的标准解法,加分项
- 死锁四条件 + 排查(jstack 直接打印 "Found one Java-level deadlock")

**源码任务(本周起进入 JUC 源码,IDEA 里打断点跟)**
- `Thread`:start0/run/interrupt/isInterrupted/state 字段;`Object.wait` 对应的 `AbstractQueuedSynchronizer$ConditionObject` 先不看,W8 再看
- `ThreadLocal`:get→getMap→setMap;ThreadLocalMap.set 的 replaceStaleEntry 探测清理;remove;`withInitial`
- `FutureTask`:run/awaitDone/get 的状态流转(为 W9 CompletableFuture 铺垫)

**实验**
1. **状态机观察**:写一个包含 sleep 线程、抢 synchronized 失败线程、wait 线程、join 线程的程序,`jstack` 打印全部线程栈,把每个线程对号入座到 6 种状态,画状态转换图
2. **中断实验三连**:
   - 中断一个 sleep 中的线程 → 捕获 InterruptedException 后验证 `isInterrupted()==false`(标志被清除)
   - 正确的生产者消费者:用 `Thread.currentThread().interrupt()` 恢复标志
   - `interrupted()` 连续调两次结果不同(静态方法清标志)
3. **wait/notify 手写生产者消费者**(固定大小缓冲区),故意制造 lost wakeup(notify 丢失)再修复为 notifyAll / while 循环检查条件(对照《JCiP》第 14 章)
4. **ThreadLocal 泄漏复现**:线程池提交任务 set 大对象不 remove,任务结束后强引用 key 置 null + System.gc,再提交新任务 get 到**上一个用户的残留值**——把"为什么线程池必须 remove"演示给自己看
5. **死锁**:构造经典 AB-BA 死锁,jstack 定位;再用 `tryLock(timeout)`(W8 学完 ReentrantLock 后回来改)和"按序加锁"两种方式修复
6. **TTL 体验**:引入 `com.alibaba:transmittable-thread-local`,对比 InheritableThreadLocal 在线程池下取值错乱 vs TTL 正确传递

**输出**:①线程状态转换图(手绘)②ThreadLocal 泄漏笔记(画 Entry 弱引用链路图)。

---

### W7 · CAS、原子类与高性能计数

**目标**:理解无锁编程的成本与收益;LongAdder 分段思想吃透(它贯穿到 ConcurrentHashMap 的 size)。

**八股清单**
- CAS:compare-and-swap 语义、x86 的 `lock cmpxchg`、Unsafe 类;三大问题:ABA(解决:AtomicStampedReference 版本号)、自旋开销(高竞争下烧 CPU)、只能保证单变量(解决:AtomicReference 封装对象 / 加锁)
- CAS vs synchronized:乐观 vs 悲观、竞争强度决定选择
- 原子类家族:基本型(AtomicInteger/Long/Boolean)、数组型、引用型(AtomicReference/Stamped/Markable)、字段型(**AtomicIntegerFieldUpdater**,零反射零额外对象,框架最爱)、累加器型(LongAdder/LongAccumulator)
- **LongAdder 原理**:base + Cell[] 分段;竞争失败才分散到 Cell;sum() 非原子快照;`@HotSpotIntrinsicCandidate`/填充避免伪共享;**为什么高并发下比 AtomicLong 快**(把"一个热点"拆成"N 个热点"——和 ConcurrentHashMap 的 CounterCell、分库分表是同一个思想)
- LongAdder vs AtomicLong 选型:统计计数用前者,CAS 语义依赖精确值(如序号生成)用后者
- 伪共享:缓存行 64B、Cell 的 padding、`@Contended` 需要 `-XX:-RestrictContended`

**源码任务**
- `AtomicInteger`:incrementAndGet→Unsafe.getAndAddInt 自旋循环(就 10 行,精读)
- `LongAdder`:add→longAccumulate(Cell 创建/探测/扩容逻辑)→sum;对照 W10 的 `ConcurrentHashMap.addCount` 会发现就是同一套 CounterCell
- `AtomicStampedReference`:version 字段如何解决 ABA
- `AtomicReferenceFieldUpdater` 用法(在 Netty/框架源码里搜使用处看一个例子)

**实验**
1. **JMH 正式入门**:写第一个 `@Benchmark`,理解 `@Warmup`/`@Measurement`/`Mode.Throughput`/`Blackhole`(防止死代码消除);报告怎么读
2. **计数三雄对决**(本周核心实验):`AtomicLong` vs `LongAdder` vs `synchronized+long`,在 1/4/16/64 线程下测吞吐量,画折线图。预期:低竞争三者接近,高竞争 LongAdder 碾压——面试讲"我压测过"和"我背过"是两个层次
3. **ABA 复现与解决**:两个线程用 AtomicReference 做栈 pop,构造 ABA 导致链表错乱;换 AtomicStampedReference 修复
4. **伪共享实验**:long[8] 各线程写不同下标 vs 8 个独立对象(或 @Contended 填充),JMH 对比;亲眼看到 3–10 倍差距
5. **Unsafe 一瞥**(JDK8):反射拿 Unsafe 调 compareAndSwapInt,理解"框架为什么能绕开语法限制";JDK9+ 对照 VarHandle

**输出**:计数三雄压测报告(截图 + 结论一段话)。

---

### W8 · AQS 与锁家族(JUC 的心脏,本计划最重的一周)

**目标**:白板画 AQS 队列讲清全流程;手写一把锁。

**八股清单**
- **AQS 框架**:volatile int state + CLH 变体双向队列(FIFO,prev/next);独占/共享两种模式;模板方法模式(子类只实现 tryAcquire/tryRelease/tryAcquireShared/tryReleaseShared/isRepeatable);为什么用 park/unpark 而不是 wait/notify(不需要先持锁、精准唤醒)
- acquire 全流程:tryAcquire 失败→addWaiter(EXCLUSIVE)→acquireQueued(自旋:前驱是 head 才再试,否则 shouldParkAfterFailedAcquire 把前驱 waitStatus 置 SIGNAL 再 park)→被唤醒后重入
- release 流程:tryRelease→unparkSuccessor(head 的后继)
- 公平 vs 非公平:非公平锁 acquire 时直接 CAS 抢 state(插队),吞吐量高但可能饥饿;ReentrantLock 默认非公平,synchronized 是非公平
- **ReentrantLock**:Sync/NonfairSync/FairSync;可重入实现(state 累加,exclusiveOwnerThread 记录持有者);对比 synchronized 的功能表:可中断(acquireNanos/acquireInterruptibly)、超时 tryLock、公平可选、多 Condition、必须手动 unlock(finally)
- **Condition**:await→释放锁→进条件队列→signal→转移到同步队列;对比 wait/notify(一把锁多个等待队列,如阻塞队列的 notFull/notEmpty)
- **ReentrantReadWriteLock**:state 高 16 位读锁计数、低 16 位写重入数;读读共享、读写/写写互斥;锁降级(支持)与锁升级(不支持,会死锁);写锁饥饿问题 → StampedLock(乐观读 tryOptimisticRead + validate)
- Semaphore(共享模式经典实现)、CountDownLatch(AQS 共享模式 + state 计数)、CyclicBarrier(ReentrantLock+Condition 实现,可重用,对比 CDL 一次性)
- park/unpark 的许可(permit)语义:unpark 可先于 park 调用(对比 notify 必须先 wait)

**源码任务(按此顺序 debug 跟读,一周刚好)**
1. `AbstractQueuedSynchronizer`:acquire→acquireQueued→parkAndCheckInterrupt;addWaiter;shouldParkAfterFailedAcquire;release→unparkSuccessor;共享模式 acquireShared→doAcquireSharedInterruptibly(读 CountDownLatch 用)
2. `ReentrantLock`:Sync.nonfairTryAcquire / FairSync.tryAcquire(就差别在 hasQueuedPredecessors 一行,精读)
3. `ConditionObject`:await(释放锁 nodeForCondition→addConditionWaiter)/ signal(transferForSignal)
4. `CountDownLatch`:CountdownSync(AQS 内部类,tryAcquireShared 判断 state==0)/ await / countDown——**最短最清晰的 AQS 共享模式教材,只有一页,必须逐行读**
5. `Semaphore`:Sync.tryAcquireShared 的自旋 CAS 扣减
6. 泛读:`ReentrantReadWriteLock`(看 state 位分割)、`StampedLock`(看乐观读思想)、`CyclicBarrier`(看它如何用 ReentrantLock+Condition 组合实现——体会"AQS 是积木")

**实验**
1. **手写 AQS 锁**(本周核心):继承 AbstractQueuedSynchronizer 实现不可重入独占锁 + 可重入版本 + tryLock(timeout) 版本;用 W6 的死锁 demo 验证 tryLock 能破死锁
2. **CountDownLatch 模拟 MapReduce**:N 个"map 线程"分片处理数据 → countDown;主线程 await → 汇总归并。**做完这个实验,shuffle 的多路归并在你眼里就是并发原语的组合**
3. **公平 vs 非公平压测**:JMH,16 线程高频 lock/unlock,对比吞吐量;再构造"插队导致某线程长时间拿不到锁"的饥饿场景
4. **双 Condition 有界阻塞队列**:手写 MyBlockingQueue(notFull/notEmpty 两个 Condition),和 W6 的 wait/notifyAll 版本对比"精准唤醒"减少的无效竞争;之后对照 `ArrayBlockingQueue` 源码验证自己写得对不对
5. **读写锁验证**:3 读 1 写场景,打印进入顺序验证读读并行、写独占;尝试"读锁内拿写锁"复现锁升级死锁
6. **Semaphore 限流**:模拟数据库连接池(W2 实验埋的坑自己填);对比 Guava RateLimiter(令牌桶)了解限流两大家族

**输出**:①AQS 白板讲解视频/讲稿(acquire 全流程 10 分钟)②手写锁 + 双 Condition 队列代码入库。**对照验收标准 3、4 自评。**

---

### W9 · 线程池(面试权重最高的单一主题)

**目标**:execute 流程倒背如流;手写迷你线程池;能结合大数据场景谈线程池设计。

**八股清单**
- 七大参数逐一理解:corePoolSize、maximumPoolSize、keepAliveTime、unit、workQueue、threadFactory(命名!排查问题全靠它)、handler
- **execute 四步流程**:worker 数 < core → 建核心线程;否则入队;队满 → 建线程到 max;再满 → 拒绝。**注意顺序是"先入队后扩到 max"**(与直觉相反,面试高频)
- 四种拒绝策略:AbortPolicy(默认抛异常)/ CallerRunsPolicy(调用者执行,天然反压——大数据场景重要)/ Discard / DiscardOldest;自定义策略(记日志+落盘重放)
- ctl 的位运算:高 3 位 runState(RUNNING→SHUTDOWN→STOP→TIDYING→TERMINATED)+ 低 29 位 workerCount,一个 AtomicInteger 管两件事(呼应 W7 的 CAS)
- 状态转换:shutdown(不接新任务,队列任务做完)/ shutdownNow(interrupt 所有 worker,返回未执行列表)/ awaitTermination;onShutdown/onShutdownNow 钩子
- **线程复用原理**:Worker.run→runWorker 循环 getTask;core 线程 `workQueue.take()` 无限阻塞,非 core `poll(keepAliveTime)` 超时返回 null→processWorkerExit 回收;allowCoreThreadTimeOut
- Worker 为什么继承 AQS(不可重入锁,用于判断线程是否空闲、shutdown 时跳过正在执行任务的 worker)——AQS 的又一次应用
- 为什么禁止 Executors:newFixedThreadPool/newSingleThreadExecutor 的 LinkedBlockingQueue 无界 → 堆 OOM;newCachedThreadPool/newScheduledThreadPool 的 max=Integer.MAX_VALUE → 线程数爆炸 OOM(阿里开发手册强制条款)
- 线程数经验公式:CPU 密集 N+1;IO 密集 2N 或 `N * (1 + W/C)`;更靠谱的方式:压测调参 + 动态线程池(美团 DynamicTp / Hippo4j,思路是参数可运行时改+监控告警)
- ScheduledThreadPoolExecutor:DelayedWorkQueue(二叉堆)、装饰器模式包装任务、周期任务异常导致后续不再执行的坑(catch 住才继续)
- **ForkJoinPool**:分治 + 工作窃取(deque 头尾分工);parallelStream 底层用 **commonPool(全局共享!)** 的坑:阻塞任务拖垮全 JVM 的并行流、ForkJoinPool.C ForkJoinPool 不适合 IO 阻塞(managedBlock)
- **CompletableFuture**:runAsync/supplyAsync(不传线程池就用 commonPool——生产必须传自定义池);thenApply(同步转换)vs thenApplyAsync vs thenCompose(扁平化,类比 flatMap);thenCombine(双流合并);异常处理 exceptionally/handle/whenComplete;allOf/anyOf;get vs join;超时控制(JDK9 orTimeout / completeOnTimeout)

**源码任务**
1. `ThreadPoolExecutor`:ctl 位运算三件套(ctlOf/runStateOf/workerCountOf);**execute**(三步判断);addWorker(双重检查 + CAS);**runWorker**(beforeExecute/afterExecute 钩子、task.run 而非 start);**getTask**(poll 超时→退出的逻辑);processWorkerExit;shutdown/shutdownNow/awaitTermination 状态机
2. `Worker` 内部类:继承 AQS 的 tryAcquire 为什么恒不可重入
3. `ScheduledThreadPoolExecutor`:schedule/decorateTask/DelayedWorkQueue.offer-take(泛读)
4. `ForkJoinPool`:泛读 work-stealing 主循环 scan→runTask→awaitDone(很难,理解思想即可,不必逐行)
5. `CompletableFuture`:uniApply/uniCompose 的组合原理(泛读)+ 精读 AsyncSupply 如何驱动依赖链

**实验**
1. **手写迷你线程池**(毕业实验):BlockingQueue + Worker 线程 + core/max/keepAlive + 优雅关闭(shutdown 等待队列清空);写单元测试验证线程复用(任务里打印线程名)
2. **参数行为观察台**:固定 core=2,max=4,分别用容量 2 的 ArrayBlockingQueue / SynchronousQueue / 无界 LinkedBlockingQueue,提交 10 个任务,打印每一步(建线程/入队/拒绝)——亲眼确认"先入队后扩 max"和 SynchronousQueue 直接扩线程的行为差异
3. **Executors 翻车复现**:newFixedThreadPool 提交百万任务,用 jstat/MAT 观察队列堆积直至 OOM(在小 -Xmx 下做,体验完整排查——W5 的 runbook 直接复用)
4. **线程池大小压测**:同一个"IO 密集"(sleep 模拟远程调用)与"CPU 密集"(计算)任务,分别用 N、2N、N*(1+W/C) 线程数跑 JMH,验证公式适用边界
5. **parallelStream 拖垮 commonPool**:commonPool 里执行阻塞 IO,另一个无关的 parallelStream 任务被饿死;换成自建 ForkJoinPool 或 CompletableFuture+自定义池修复
6. **CompletableFuture 编排实战(大数据味)**:模拟"并行查询 3 个数据源(HDFS 元数据/MySQL/Kafka)→ thenCombine 合并 → 超时降级(其中一个故意 sleep 超时)→ exceptionally 兜底",全链路计时对比串行
7. **ScheduledThreadPoolExecutor 异常坑**:scheduleAtFixedRate 任务抛异常后不再调度,catch 后恢复——写成对比 demo

**输出**:迷你线程池代码 + "execute 四步流程图"手绘。

---

### W10 · 并发容器

**目标**:ConcurrentHashMap 源码级掌握(它是 JUC 集大成者:CAS+synchronized+分段思想全在里面)。

**八股清单**
- **ConcurrentHashMap(JDK8)**:结构 Node[] + 链表(≤8)→ 红黑树(TreeBin,注意树化还要 table 长度 ≥64,否则先扩容);**put 流程**:table 未初始化→initTable(CAS 抢 sizeCtl 初始化);桶空→CAS 放头节点(无锁!);桶非空→synchronized 锁头节点(锁粒度:一个桶);正在扩容→helpTransfer 帮忙搬;树节点→putTreeVal
- **size 怎么算**:addCount → baseCount + CounterCell[](LongAdder 同款分段),sumCount 是弱一致快照;为什么不能精确(并发下"精确 size"本身无意义)
- **并发扩容 transfer**:每个线程认领一段桶(stride ≥16);ForwardingNode(hash=MOVED)标记已迁移桶,读请求转发到新表;sizeCtl 的多重含义(负数=扩容中,高 16 位扩容戳/低 16 位参与线程数+1)
- 为什么 key/value 不允许 null:并发下 `get(k)==null` 无法区分"不存在"还是"值为 null"(歧义),HashMap 单线程可用 containsKey 二次确认
- JDK7 vs 8:Segment 分段锁(ReentrantLock)→ CAS+synchronized(锁粒度桶级);JDK7 头插并发扩容死循环问题、JDK8 尾插+高低位拆分(lo/hi 链)不再死循环但仍会丢数据/size 不准
- 读操作为什么基本无锁:Node.val 和 next 都是 volatile
- **CopyOnWriteArrayList**:写时复制(加 ReentrantLock,复制数组写完替换引用),读无锁;适用读极多写极少(监听器列表);缺点:写 O(n)+内存翻倍+弱一致迭代器;对比 Collections.synchronizedList(全表一把锁)
- 阻塞队列全家福对比表:ArrayBlockingQueue(一把 ReentrantLock + notEmpty/notFull 双 Condition,呼应 W8 手写)、LinkedBlockingQueue(takeLock/putLock 两把锁,读写分离,节点无界默认 Integer.MAX_VALUE 的 OOM 风险)、SynchronousQueue(无容量,直接交接,Transferer,cachedThreadPool 用它)、PriorityBlockingQueue(二叉堆+单锁)、DelayQueue(PriorityBlockingQueue+Delayed,定时任务/缓存过期实现基础)
- ConcurrentSkipListMap:跳表 vs 红黑树(为什么并发场景选跳表:局部 CAS 即可插入、范围查询友好、实现简单;CHM 没用它因为散列已够快)
- 弱一致迭代器 vs fail-fast(HashMap 的 modCount)

**源码任务**
1. `ConcurrentHashMap`(**本周主菜,精读**):spread(hash 扰动,高 16 位异或 + 与 HASH_BITS)、initTable、**putVal**(五条分支逐行)、**transfer**(最难,理解认领机制和 ForwardingNode 即可)、addCount/sumCount/fullAddCount(对照 LongAdder,几乎同款)、get(无锁路径)、treeifyBin
2. `CopyOnWriteArrayList`:add/set 的锁 + 数组复制、迭代器为什么不支持 remove
3. `ArrayBlockingQueue`:入队出队全流程(你 W8 手写过的对照答案)
4. `LinkedBlockingQueue`:两把锁的 enqueue/dequeue、AtomicInteger count 的 CAS
5. 泛读:`ConcurrentSkipListMap` 的 doPut(看 CAS 无锁插入思想)、`DelayQueue.take`(Leader-Follower 模式减少无效唤醒)

**实验**
1. **HashMap 并发翻车**:N 线程往 HashMap put,循环多轮观察 size 丢失/put 返回后 get 不到(JDK8 不死循环但数据错);换 ConcurrentHashMap 和 Collections.synchronizedMap 对比正确性与吞吐(JMH)
2. **CHM 并发扩容观察**:小容量初始化(-D 或直接反射拿 table 长度)多线程猛 put,断点/日志观察 ForwardingNode 出现和 helpTransfer 生效;打印 treeify 前后桶结构(链表长度超 8 且表长 ≥64)
3. **size 的弱一致性**:一边 put 一边循环打印 size(),对照单线程 HashMap——亲眼看到"快照"语义
4. **三种生产者消费者终极对比**:wait/notify 版(W6)→ 双 Condition 手写版(W8)→ ArrayBlockingQueue 版,JMH 吞吐对比,理解"封装层级换开发效率"的取舍
5. **COW 读写比实验**:高读低写场景 COW vs synchronizedList 吞吐对比;再演示写多场景 COW 的内存暴涨(jstat 观察 GC)
6. **DelayQueue 实现本地缓存过期**:put 带过期时间的 key,take 阻塞到期弹出——这就是 Guava Cache/Caffeine 过期机制的玩具版(顺带为以后读 Caffeine 铺路)

**输出**:CHM putVal 五条分支流程图(手绘)+ 并发容器选型对比表(自己整理,面试可直接用)。

---

### W11 · 融合周:Netty 线程与内存模型 + 大数据场景串联

**目标**:把 JVM+并发知识接到大数据框架上——这一周决定你面试时能否"降维打击"。

**八股/知识清单**
- **Reactor 线程模型**:单/多 Reactor;Netty 的 boss/worker EventLoopGroup;**一个 EventLoop = 一个线程 + 一个 Selector + 一个任务队列**,Channel 终身绑定一个 EventLoop(所以 Handler 内不需要考虑线程安全,但**绝不能阻塞 EventLoop**——阻塞一个 loop 卡死它管的所有连接)
- Pipeline 责任链:ChannelHandler 顺序、inbound/outbound、`ctx.fireChannelRead` 传播;为什么 Netty 大量用无锁设计(EventLoop 内串行化取代加锁——"以线程模型消灭并发问题",比锁更高明的思路)
- **ByteBuf vs NIO ByteBuffer**:读写双指针不用 flip、动态扩容、引用计数(ReferenceCounted,手动 release,内存泄漏检测器 ResourceLeakDetector)
- **池化内存**:PooledByteBufAllocator 的 Arena/Chunk/Page/SubPage(buddy 分配 + jemalloc 思想);为什么池化(减少 GC 压力+避免堆外分配的系统调用);`-Dio.netty.leakDetection.level=paranoid`
- 堆外内存:Netty 用 DirectByteBuf 的理由(少一次堆内堆外拷贝);PlatformDependent 自己管理堆外分配(绕开 MaxDirectMemorySize 的 Cleaner 机制,用 AtomicLong 记账)——W5 堆外实验的直接升华
- **零拷贝四件套**:transferTo/sendfile(内核态直发,不经过用户态)、CompositeByteBuf(逻辑聚合)、slice/duplicate(共享存储)、FileRegion;对照 **Kafka 为什么快**(顺序写 + pagecache + sendfile)
- JDK epoll 空轮询 bug 及 Netty 的重建 Selector 方案
- **大数据场景串联(把前面的知识全部挂钩)**:
  - Hadoop/Spark shuffle:溢写=堆内存不够时写磁盘(为什么要控制堆大小)、shuffle 网络传输=Netty、排序=外部归并(内存受限的经典权衡)
  - Flink:managed memory 自己管堆外(为什么 Flink 不受 GC 折磨)、RocksDB 状态后端=堆外+磁盘、checkpoint barrier 对齐=并发协调、`taskmanager.memory.*` 参数体系=JVM 内存区域的应用题
  - Kafka:顺序写、pagecache(OS 缓存而非 JVM 堆——所以 Kafka 堆给 6G 就够)、零拷贝、batch+linger.ms=生产者攒批(W7 计数/批量思想)
  - HBase:MemStore(堆内)+ BlockCache(可配堆外 BucketCache)+ 写屏障式 flush,LSM 思想
  - 大数据 JVM 调优共识:大堆(数十 G)选 G1/ZGC、警惕堆外、GC 停顿对心跳超时/任务失败的影响(YARN container 被杀、Flink heartbeat timeout)

**源码任务(定点,不通读)**
1. `NioEventLoop.run()`:select→processSelectedKeys→runAllTasks 主循环;taskQueue 的加入与执行(呼应"EventLoop 内串行化")
2. `ByteBufAllocator`→`PoolArena.allocate` 主干(理解大小规格分类:tiny/small/normal/huge 即可)
3. `ChannelPipeline.fireChannelRead` 传播机制
4. 对照读一段 Flink:`MemorySegment`(就是 byte[] 或 DirectByteBuffer 的封装 + unsafe 读写)——一小时能读完,读完你就懂"框架如何自己管内存"

**实验**
1. **EventLoop 阻塞演示**:Netty server 的 handler 里 sleep 10s,多客户端连接全部卡住;把耗时逻辑丢进自定义业务线程池(呼应 W9"为什么 handler 里不能用 commonPool")后恢复——**这个实验等于把 W6-W9 全部串起来**
2. **堆内 vs 堆外 vs 池化 ByteBuf**:JMH 对比分配+读写性能;开 `-Dio.netty.leakDetection.level=paranoid` 故意不 release,看泄漏报告
3. **零拷贝验证**:传统 read+write 循环 vs `FileChannel.transferTo` 复制 1GB 文件,对比耗时与用户态拷贝次数(可用 strace 或 JFR 观察系统调用)
4. **NMT 观察 Netty 堆外**:`-XX:NativeMemoryTracking=summary` 启动 Netty 应用,`jcmd VM.native_memory summary.diff` 看 Internal/Other 段增长——堆外内存排查实战
5. **Flink 内存模型沙盘**(概念实验):按 Flink 文档画一张 `taskmanager.memory.process.size` 的完整分解图(JVM Heap / Managed / Network / Metaspace / Overhead),标注每块对应你学过的哪个 JVM 知识点

**输出**:一张"大数据组件 × JVM/并发知识点"映射表(shuffle↔排序与堆外、Kafka↔零拷贝与顺序写、Flink↔托管内存、Netty↔线程模型),这张表就是你面试的杀手锏。

---

### W12 · 输出周:总复习、模拟面试与综合大作业

**目标**:知识变现为面试战斗力。

**安排**
1. **Anki 卡片冲刺**(每天 30 分钟贯穿全程更好,本周集中补):把 12 周所有"必答题"做成问答卡,间隔重复。做卡原则:一张卡一个问题,答案用自己的话+实验证据
2. **费曼输出**:挑 5 个最重主题(AQS、G1、线程池、ConcurrentHashMap、volatile/happens-before)各写一篇博客或录一段 10 分钟讲解。写不出来说明没懂,回去重读
3. **模拟面试**:用附录 50 题自测,录音回答,听回放找卡壳点;有条件找人对练
4. **综合大作业(毕业设计)**——"多线程日志聚合统计工具":
   - 需求:扫描目录下 N 个大日志文件(可自己生成 10×500MB),统计每个 IP 的访问次数、按小时聚合错误数
   - 技术要求全覆盖:线程池读文件(参数压测调优)→ ConcurrentHashMap+LongAdder 计数 → CompletableFuture 编排汇总 → 超时降级 → 优雅关闭(shutdown hook)
   - 工程要求:JMH 对比至少两种计数方案并写进 README;`-Xlog:gc*` 全程记录,调优前后各跑一遍,附 GCeasy 对比报告;用 JFR + Arthas 各做一次自查(火焰图、线程热点)
   - 加分项:用 NIO FileChannel + 直接缓冲区读文件;把工具打进 jar,README 写清"为什么这样设计线程数和队列"
5. **自评**:逐条过第 0 节验收标准,不达标的项回到对应周补

---

## 4. 资源清单(精,不贪多)

**书(就 3 本,别买更多)**
1. 《深入理解 Java 虚拟机》第 3 版(周志明)——JVM 线主教材。**精读**:第 2 章(内存区域)、第 3 章(GC)、第 4 章(工具,配合实验)、第 8 章(字节码)、第 12 章(JMM)、第 13 章(线程安全与锁优化)。第 5 章(版本)泛读
2. 《Java 并发编程实战》(JCiP)——并发线思想教材(偏原则)。重点:2-5 章(组合对象安全)、10 章(死锁)、12-14 章(性能、显式锁、条件队列)。**注意它基于 JDK5,原子类/线程池实现细节以现在的源码为准**
3. 《Java 并发编程的艺术》——更贴国内面试与 AQS/线程池源码讲解,作为 JCiP 的源码补充

**在线资料**
- Doug Lea 论文:*The java.util.concurrent Synchronizer Framework*(AQS 原始设计论文,W8 配套读)
- JSR-133 FAQ(JMM 权威解释,volatile/DCL 章节)
- 美团技术博客:《Java NIO 浅析》《Java 8 系列之重新认识 HashMap》、线程池与动态线程池相关文章
- 官方 JEP:248(G1 默认)、333(ZGC)、344/345(ZGC 相关)、374(偏向锁废弃)
- 源码在线看:github.com/openjdk/jdk(tag 选 jdk8/jdk17)、github.com/netty/netty、github.com/apache/flink

**工具**:JOL、JMH、Arthas、MAT、JMC(JFR)、GCeasy、VisualVM(见第 2 节)

---

## 5. 学习节奏与方法论

**每日节奏(工作日 1.5–2h)**
- 30 min:八股清单阅读(JavaGuide/书对应章节)+ 做 Anki 卡
- 45–60 min:源码 debug 或实验(交替)
- 睡前 10 min:Anki 复习昨日卡片

**周末(3–4h/天)**:集中做当周大实验 + 写输出笔记。

**五条铁律**
1. **每个八股结论至少亲手验证一个**——实验列不是可选项,是这个计划和"背八股"的本质区别
2. **源码用 debug 不用眼睛**:写 20 行调用代码,断点打在目标方法,F8 单步走一遍胜过读十遍
3. **画图**:每读一个核心类,画对象关系图 + 关键流程时序图,存进笔记库
4. **版本意识**:所有结论标注 JDK 版本(JDK7/8/17 行为差异本身就是考点)
5. **输出倒逼输入**:每周日写 500 字周记"我本周能讲明白的 3 件事",讲不明白的下周优先补

**进度弹性**:某周内容重(AQS、CHM、线程池三周最重),允许拆成 1.5–2 周;W1、W7 较轻可合并。顺序不建议调换,依赖关系是设计过的(如 LongAdder→CHM 计数、Condition→ArrayBlockingQueue、堆外内存→Netty)。

---

## 6. 附录:模拟面试 50 题(最终自测)

### JVM(20 题)
1. JVM 内存区域划分?哪些线程共享/私有?分别可能抛什么错误?
2. JDK8 为什么用元空间替换永久代?
3. 对象创建的完整过程?TLAB 是什么?
4. 对象内存布局?Mark Word 存了什么?
5. 对象一定在堆上分配吗?逃逸分析和标量替换?
6. 如何判断对象已死?GC Roots 有哪些?
7. 强软弱虚引用的区别与应用场景?
8. 三大 GC 算法及各自适用分代?为什么新生代 Eden:S0:S1=8:1:1?
9. Minor GC / Major GC / Full GC 触发条件?
10. 跨代引用怎么解决?卡表和写屏障?
11. CMS 的四个阶段?并发模式失败是什么?为什么被移除?
12. G1 的核心设计(Region/RSet/SATB/可预测停顿)?什么场景选 G1?
13. ZGC 为什么能做到亚毫秒停顿?染色指针和读屏障?
14. 类加载过程五个阶段?准备和初始化的区别?
15. 双亲委派机制及意义?三个破坏案例?
16. volatile 的底层实现?为什么不能保证原子性?
17. DCL 单例为什么要加 volatile?不加会怎样?
18. happens-before 是什么?八大规则?
19. synchronized 锁升级过程?锁消除和锁粗化?
20. 线上 CPU 100% / 频繁 Full GC / 堆外内存泄漏,分别怎么排查?(说出完整命令链)

### 并发(30 题)
21. 线程有哪 6 种状态?sleep 和 wait 的区别?
22. 如何正确停止一个线程?为什么 stop 被废弃?InterruptedException 抛出后标志位是什么状态?
23. start 和 run 的区别?线程安全的单例有哪几种写法?
24. CAS 是什么?三大问题及解决?
25. AtomicLong 和 LongAdder 的区别?什么时候必须用 AtomicLong?
26. 什么是伪共享?怎么解决?
27. ThreadLocal 原理?为什么会内存泄漏?线程池下为什么必须 remove?
28. InheritableThreadLocal 在线程池下为什么失效?TTL 怎么解决?
29. AQS 的核心设计?state 和 CLH 队列?
30. 讲一遍 ReentrantLock.lock() 的完整流程(非公平)?
31. 公平锁和非公平锁的区别?为什么默认非公平?
32. synchronized 和 ReentrantLock 的区别?(至少 5 点)
33. Condition 和 wait/notify 的区别?
34. CountDownLatch 和 CyclicBarrier 的区别?各自底层?
35. 读写锁的锁降级?为什么不支持锁升级?StampedLock 改进了什么?
36. 死锁的四个必要条件?怎么排查?怎么预防?
37. 线程池七大参数?execute 的完整流程?为什么是先入队再扩 max?
38. 线程池的线程是怎么复用的?keepAliveTime 对核心线程生效吗?
39. 为什么阿里规范禁止用 Executors 创建线程池?
40. 线程池大小怎么设置?CPU 密集和 IO 密集的区别?
41. shutdown 和 shutdownNow 的区别?如何优雅关闭?
42. Worker 为什么继承 AQS?
43. ConcurrentHashMap JDK7 和 8 的实现区别?
44. CHM 的 put 流程?什么时候 CAS 什么时候 synchronized?
45. CHM 的 size() 是精确的吗?怎么实现的?
46. CHM 为什么 key/value 不能为 null?HashMap 可以吗?
47. HashMap 并发下会出什么问题?JDK7 的死循环怎么产生的?
48. CopyOnWriteArrayList 的原理和适用场景?缺点?
49. 常见阻塞队列对比?ArrayBlockingQueue 和 LinkedBlockingQueue 锁设计区别?
50. CompletableFuture 和 Future 的区别?thenApply 和 thenCompose 的区别?为什么不指定线程池很危险?

**评分标准**:每题能脱口而出主干 + 至少一题能讲出源码细节或实验证据 = 及格;40 题以上达到 = 大数据岗 Java 面试竞争力前列。

---

## 7. 里程碑检查点

| 时间 | 检查点 | 不达标动作 |
|------|--------|-----------|
| W5 末 | JVM 毕业:能画内存图 + 独立完成三类排障 | W6 前半周回补,后面顺延 |
| W8 末 | 能白板讲 AQS | 重读 CountDownLatch 源码(W12 前必须过) |
| W10 末 | 并发毕业:50 题并发部分自测 ≥22/30 | W11 减量,腾时间刷薄弱周 |
| W12 末 | 大作业完成 + 50 题总自测 ≥40 | 进入"面经实战期",边投边补 |
