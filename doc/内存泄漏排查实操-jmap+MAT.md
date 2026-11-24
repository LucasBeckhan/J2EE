# 内存泄漏排查实操：一段故意泄漏的代码 + jmap + MAT 全流程

> 本文所有命令输出都是**在本仓库 `jvm-lab` 模块上真实跑出来的**（Windows + JDK 27 + MAT 1.17.0），
> 不是示意数据。你照着「第 9 节 动手清单」敲，就能得到几乎一样的数字。
>
> 配套代码：`jvm-lab/src/main/java/com/jvmlab/LeakDemo.java`（泄漏版）、`LeakDemoFixed.java`（修复版）。

---

## 全景图：这次排查走过的路

```
写一段会泄漏的代码
        │
        ▼
① jps 找到进程 PID          ── 「哪个进程出事？」
        │
        ▼
② jmap -histo:live × 2 次   ── 「谁在只涨不跌？」→ 锁定嫌疑类
        │
        ▼
③ jmap -dump 抓堆快照        ── 「把案发现场封存下来」
        │
        ▼
④ MAT 打开 dump             ── 「谁引用着它？」→ 精确到类名、字段名、代码行号
        │
        ▼
⑤ 修复 + 同样手段复测        ── 「结案」
```

一句话心法：**jmap 负责"看得见"，MAT 负责"看得懂"。** histogram 告诉你*什么*在泄漏，MAT 的引用链告诉你*为什么*泄漏不了。

---

## 0. 准备：先把"案发条件"造出来

### 0.1 泄漏代码长什么样

完整代码在 [LeakDemo.java](../jvm-lab/src/main/java/com/jvmlab/LeakDemo.java)，核心只有两行：

```java
// 第 23 行：★泄漏点——static 集合只进不出
private static final List<Task> FINISHED_TASKS = new ArrayList<>();

// 第 46 行：每处理完一个任务，塞进去，但全程序没有任何地方清理它
FINISHED_TASKS.add(new Task("task-" + n, payloadBytes));
```

任务对象自带 2KB 的 payload（`byte[]`），让它在统计里"大到肉眼可见"：

```java
static class Task {
    final String id;
    final byte[] payload;          // 2 KB
    Task(String id, int payloadBytes) {
        this.id = id;
        this.payload = new byte[payloadBytes];
    }
}
```

### 0.2 为什么这几行代码就是"内存泄漏"？（必须先懂的原理）

先澄清一个新手误区：**内存泄漏 ≠ 内存不够**。

JVM 判断一个对象能不能回收，只看一件事：**从 GC Root 出发，还能不能"摸到"这个对象**。
摸得到 → 活对象，不许回收；摸不到 → 垃圾，下次 GC 回收。

GC Root 常见的有三类（知道这个够了）：

| GC Root | 例子 |
|---|---|
| 正在运行的线程栈里的局部变量 | `main` 方法里的 `task` 变量 |
| **类的 static 字段** | **`LeakDemo.FINISHED_TASKS` ← 本案凶器** |
| JVM 内部引用 | 系统类加载器持有的类对象等 |

所以本案的因果链是：

```
FINISHED_TASKS 是 static 字段（GC Root）
    └─> ArrayList 永远可达
            └─> 里面的每个 Task 永远可达
                    └─> 每个 Task.payload (2KB byte[]) 永远可达
                            └─> GC 永远不敢回收，堆只进不出 → 直到 OOM
```

「处理完」的任务在业务上已经是垃圾了，但**程序还留着它的引用**——垃圾放不进垃圾桶，这就是内存泄漏。

### 0.3 启动参数：为什么故意给小堆

```bash
java -Xmx64m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=dumps \
     -cp target/classes com.jvmlab.LeakDemo
```

| 参数 | 作用 | 为什么这么配 |
|---|---|---|
| `-Xmx64m` | 堆上限 64MB | 生产环境泄漏几小时~几天才炸。64MB + 每秒约 90 个任务 × 2KB ≈ **5 分钟 OOM**，把"案发周期"压缩进一杯咖啡的时间 |
| `-XX:+HeapDumpOnOutOfMemoryError` | OOM 那一刻自动抓堆快照 | 生产标配。真炸了也能"验尸"，不用靠猜 |
| `-XX:HeapDumpPath=dumps` | 快照存到哪 | 不配则存到进程工作目录，文件名是 `java_pid<PID>.hprof` |

---

## 1. 第一步：jps —— 找到出事的进程

排查一切 JVM 问题的入口：先拿到 PID。**不要去任务管理器里找**——任务管理器里几十个 `java.exe` 长得一模一样，而 jps 列出的是每个 JVM 的**主类名**，一眼锁定。

```bash
jps -l
```

本机真实输出（`-l` 输出完整包名）：

```
30640 com.jvmlab.LeakDemo        ← 就是它，PID = 30640
33156 com.intellij.idea.Main
28632 org.jetbrains.idea.maven.server.RemoteMavenServer36
28540 jdk.jcmd/sun.tools.jps.Jps ← jps 自己，忽略
```

逐列拆解：
- 第 1 列 **PID**：后面 `jmap` 全靠它；
- 第 2 列 **主类全名**：`com.jvmlab.LeakDemo` 就是我们要的进程，IDEA、Maven server 都是干扰项。

> Linux 上等价命令：`ps -ef | grep java` 或 `jps -l`（jps 是跨平台的）。
> 看不到目标进程？大概率它和 jps 不在同一个用户/权限下，加 `sudo` 再试。

---

## 2. 第二步：jmap -histo:live —— 抓现行（本篇最核心的一步）

### 2.1 命令和第一次输出

```bash
jmap -histo:live 30640
```

启动后约 25 秒抓的**真实输出**（截取前几行）：

```
 num     #instances         #bytes  class name (module)
-------------------------------------------------------
   1:         18594        6531096  [B (java.base@27.0.1.1)
   2:         15659         375816  java.lang.String (java.base@27.0.1.1)
   3:          1777        234616  java.lang.Class (java.base@27.0.1.1)
   ...
   9:          2855          45680  com.jvmlab.LeakDemo$Task
```

**怎么读这张表**（比命令本身更重要）：

| 列 | 含义 | 本例 |
|---|---|---|
| `num` | 按占用字节数的排名 | 第 1 名是 `[B` |
| `#instances` | 该类的对象个数 | 18,594 个 byte[] |
| `#bytes` | 这些对象**自身**占的字节（shallow size） | 约 6.2 MB |
| `class name` | 类名。**`[B` = byte[]**，`[C`=char[]，`[I`=int[]，`[Ljava.lang.Object;`=Object[] | 榜一是 byte[] |

> 内部类型符号速记：`[` 开头就是数组，后面跟元素类型缩写——B=byte, C=char, I=int, J=long, L类名;=引用类型。`(java.base@27.0.1.1)` 是 JDK 模块标记，忽略即可。

### 2.2 关键：单看一次没意义，要"二次对比法"

堆里永远有几十万个对象，怎么区分"正常工作集"和"泄漏"？
**答案：隔 60~90 秒抓两次，只看只涨不跌的业务类。**

83 秒后的第二次输出：

```
 num     #instances         #bytes  class name (module)
-------------------------------------------------------
   1:         33331       21925672  [B (java.base@27.0.1.1)      ← 从 18,594 涨到 33,331
   2:         23023         552552  java.lang.String
   ...
   7:         10228         163648  com.jvmlab.LeakDemo$Task     ← 从 2,855 涨到 10,228
```

做成对照表，嫌疑瞬间清晰：

| 类 | 第一次 (25s) | 第二次 (108s) | 涨幅 | 判决 |
|---|---|---|---|---|
| `[B` (byte[]) | 18,594 个 / 6.2 MB | 33,331 个 / 20.9 MB | **+14,737 个 / +14.7 MB** | 涨幅 ≈ 泄漏速度 180 KB/s，**主犯** |
| `LeakDemo$Task` | 2,855 个 | 10,228 个 | **+7,373 个** | **同伙**（Task 里有 id，id 是 String，所以 String 也在涨） |
| `java.lang.Class` | 1,777 个 | 1,773 个 | ±0 | 无罪（JVM 自身工作集，正常波动） |

> **三方对账（教学点）**：此刻程序自己的日志打印 `[108s] 已处理 10250 个任务`，histogram 里 Task 是 10,228 个，只差 22 个（打印日志和抓快照之间程序还在跑）。三个独立来源——程序日志、jmap、后面 MAT——数字对得上，说明观测可信。

### 2.3 `:live` 这个参数你必须知道的两面

- 含义：**先强制一次 full GC，再统计"活下来"的对象**。
- 好处：垃圾被清场，剩下的全是有引用的——**"GC 之后还在涨"是泄漏的铁证**。不加 `:live` 的话，统计混入待回收垃圾，数字忽大忽小没法用。
- 代价：full GC = Stop-The-World，生产大堆上可能卡几秒。**生产环境请在低峰期做，或先用 `jmap -histo <pid>`（不加 live）粗看**。

到这里我们知道了"**什么**在泄漏"（Task + byte[]），但还不知道"**谁**拽着它们不放"——这正是 MAT 登场的理由。

---

## 3. 第三步：jmap -dump —— 封存案发现场

### 3.1 命令逐段拆解

```bash
jmap -dump:live,format=b,file=dumps/leak1.hprof 30640
#      └┬┘ └┬─┘ └────┬────┘ └─────────┬────────┘ └──┬──┘
#     先GC  二进制   hprof 格式      存到哪         目标 PID
```

- `live`：只 dump 活对象（和 histogram 的 `:live` 同一个意思，同样会先 full GC）；
- `format=b`：二进制格式，MAT 吃的就是这个（b = binary）；
- `file=`：输出文件，约定俗成用 `.hprof` 后缀。

真实输出：

```
Dumping heap to D:\IdeaProjects\J2EE\jvm-lab\dumps\leak1.hprof ...
Heap dump file created [27670839 bytes in 0.022 secs]
```

26 MB 的堆，0.022 秒完成——但**进程全程暂停**（STW）。生产 8GB 堆 dump 可能停顿几秒~十几秒、文件约等于堆大小，所以：

> 生产时机三原则：①低峰期抓；②确认磁盘够（文件≈堆大小）；③抓完尽快摘流量重启。

### 3.2 时机选择：半场抓 vs OOM 抓

| | 半场抓（本案 leak1.hprof） | OOM 自动抓（见 §4） |
|---|---|---|
| 时机 | 泄漏进行到一半 | 堆已耗尽那一刻 |
| 特点 | 现场"干净"，嫌疑对象突出 | 罪证最全，但堆里塞满边角垃圾 |
| 实操 | **首选**：histo 确认在涨就立刻抓 | 兜底：人不在机器旁时的保险 |

---

## 4. 并行路线：OOM 自动留证据

如果没时间盯现场，让 JVM 自己在咽气前按下"拍照键"（参数见 §0.3，快进版 `8192 2` 参数 18 秒就 OOM）。真实输出：

```
[ 18s] 已处理 7700 个任务，FINISHED_TASKS.size=7700，payload 累计约 60 MB
java.lang.OutOfMemoryError: Java heap space
Dumping heap to dumps\java_pid9300.hprof ...
Heap dump file created [69293455 bytes in 0.035 secs]

Exception: java.lang.OutOfMemoryError thrown from the UncaughtExceptionHandler in thread "main"
```

三个细节值得看：
1. `-Xmx64m`，累计 payload 60MB 时爆——数字对得上（还有 JVM 自身开销）；
2. 文件名自动取 `java_pid9300.hprof`（PID 是当时的 9300），**OOM 现场自动封存**；
3. dump 完成后进程才真正退出——JVM 尽责地留下了遗书。

---

## 5. 第四步：安装 MAT 并打开 dump

### 5.1 安装（Windows，解压即用）

- 官网 https://eclipse.dev/mat/ （Downloads → Windows (x86_64) zip）
- 国内镜像（本机实际用的）：https://mirrors.tuna.tsinghua.edu.cn/eclipse/mat/1.17.0/rcp/ 下载 `MemoryAnalyzer-1.17.0.20260601-win32.win32.x86_64.zip`，解压到任意目录，双击 `MemoryAnalyzer.exe` 即可，无需安装。

> 一个常见坑：MAT 默认只吃 ~1GB 内存，打开生产大 dump 会报 `An internal error occurred during "Parsing heap dump"`。
> 修复：编辑 MAT 目录下 `MemoryAnalyzer.ini`，把 `-Xmx1024m` 改成 dump 大小的 1.5~2 倍（10GB dump → `-Xmx16g`）。

### 5.2 GUI 打开（推荐读者动手）

1. `File → Open Heap Dump...` 选 `dumps/leak1.hprof`；
2. 首次打开弹出向导，直接选 **Leak Suspects Report** → Finish；
3. MAT 会先建索引（生成一堆 `.index` 文件，第二次打开同一 dump 秒开——所以 dump 别乱删索引）。

### 5.3 无 GUI 的服务器：命令行出报告（本机实测的路线）

MAT 自带 headless 解析脚本，在 MAT 安装目录执行：

```bash
ParseHeapDump.bat D:\...\dumps\leak1.hprof org.eclipse.mat.api:suspects
```

跑完在 dump 旁边生成 `leak1_Leak_Suspects.zip`（内含 index.html 报告）。**生产机器没显示器时，把 hprof 拖回本地用 GUI 看当然更好；但如果只想快速拿到结论，这条命令 30 秒出报告。**

---

## 6. 第五步：读懂 Leak Suspects 报告（逐句翻译）

这是整个流程的高潮。MAT 自动分析后的真实报告全文，我们一句句拆：

### 6.1 半场抓的 leak1.hprof

> **Problem Suspect 1**
> The class `com.jvmlab.LeakDemo`, loaded by jdk.internal.loader.ClassLoaders$AppClassLoader @ 0xffe2c9e0, **occupies 22,007,056 (92.53%) bytes**. The top consumers of its minimum retained heap are `byte[]` (20,491 instances totaling 21,457,624), `java.lang.String` (10,250 instances totaling 246,000), and `com.jvmlab.LeakDemo$Task` (10,241 instances totaling 245,784). The memory is **accumulated in one instance of `java.lang.Object[]`**, loaded by \<system class loader\>, which occupies 22,004,936 (92.52%) bytes.

逐句翻译：

| 报告原句 | 人话 | 破案价值 |
|---|---|---|
| `com.jvmlab.LeakDemo ... occupies 22,007,056 (92.53%)` | LeakDemo 这个类的对象们总共"兜底持有" 21 MB，**占整个堆 92.53%** | 一句话锁定嫌疑人类。你不用在几十万个对象里捞，MAT 直接说"92% 的内存和你这个类有关" |
| top consumers: byte[] 20,491 个 / String / Task 10,241 个 | 这 21MB 里绝大部分是 byte[]（就是 Task 的 payload） | 和 jmap histogram 的结论互相印证（histo: 10,228 ≈ MAT: 10,241，抓取时刻略不同） |
| accumulated in **one instance of java.lang.Object[]** | 这些对象最终都挂在**同一个 Object[] 数组**下 | 破案关键！`new ArrayList<>()` 的底层存储就是 `Object[] elementData`——**"一个 Object[]" ≈ "一个 ArrayList"** |

这里出现了一个必须搞懂的概念：

### 6.2 Shallow vs Retained Heap（MAT 的灵魂概念）

```
FINISHED_TASKS (ArrayList)          ← shallow: 对象头+几个字段 ≈ 48 字节，小得可怜
   │
   └─ elementData (Object[])        ← shallow: 数组本身 ≈ 40KB
        ├─ Task[0] ── payload byte[2048]
        ├─ Task[1] ── payload byte[2048]   ← retained: 上面所有加起来 ≈ 22MB
        └─ ... × 10,241
```

- **Shallow heap**：对象自己那一块。ArrayList 本体只有几十字节——只看 shallow，你永远找不到它；
- **Retained heap**：把this对象回收掉，**顺带能释放**的总内存。因为 10,241 个 Task 全靠这条引用链活着，所以 ArrayList 的 retained = 全部 Task + payload = 22 MB。

MAT 的排序、Suspects 的百分比，用的都是 retained heap——**这就是它能"一击命中"的原因：泄漏者的特征是 retained 特别大。**

### 6.3 OOM 自动抓的 java_pid9300.hprof（更狠：直接给到代码行）

> **Problem Suspect 1**
> One instance of `java.util.ArrayList` loaded by \<system class loader\> **occupies 63,938,288 (97.29%) bytes**. ... The memory is accumulated in one instance of `java.lang.Object[]` ... **The instance is referenced by `java.lang.Thread @ 0xfc1be168 main`** ...
> **Significant stack frames and local variables:**
> `com.jvmlab.LeakDemo.main([Ljava/lang/String;)V (LeakDemo.java:46)`

三个升级点：

1. 这回直接点名 `java.util.ArrayList` 一个实例 retained 63.9MB（97.29%）——比 92% 更夸张，因为 OOM 时堆里已经没有别的杂物了；
2. 给出了引用来源：`main` 线程 —— 结合我们源码，main 线程的类里正持有 `FINISHED_TASKS`；
3. **直接给出代码行号 `LeakDemo.java:46`** —— 对照源码，第 46 行正是：

```java
FINISHED_TASKS.add(new Task("task-" + n, payloadBytes));   // ← MAT 指认的行
```

到这里，从"感觉内存不对"到"就是这行代码"，闭环完成。

> 顺带解释两个 dump 数字为何不同：leak1 是 2KB payload 抓于 108 秒（Task 10,241 个）；OOM dump 是 8KB payload 抓于 18 秒（Task 7,711 个）。**两次是不同参数的独立运行**，各自内部自洽即可。

### 6.4 GUI 里进一步坐实（建议动手点一遍）

报告页点击嫌疑条目 → 详情页，按这个顺序点：

1. **Dominator Tree**（支配树）：按 retained 排序的"吞内存排行榜"，你会看到榜首是 `ArrayList` ≈ 22MB，展开它能看到一排排 `Task`；
2. 右键这个 ArrayList → **Path to GC Roots → exclude weak/soft references**：显示从 GC Root 到它的完整引用链。本案会看到 `LeakDemo.FINISHED_TASKS` 静态字段——**这就是"谁拽着不放"的最终答案**；
3. 排除 weak/soft 的原因：弱/软引用不阻止回收（不构成泄漏），排除掉剩下的才是"硬后台"。

---

## 7. 修复 + 同样手段复测（排查不做闭环 = 白排）

### 7.1 修复思路

泄漏的根因是"为了统计，把 2KB 的任务对象全留下了"。统计只需要数字，不需要对象：

```java
// 修复版 LeakDemoFixed.java：只留 8 字节的计数，不留 2KB 的对象引用
private static final AtomicLong FINISHED_COUNT = new AtomicLong();   // 个数
private static final AtomicLong FINISHED_BYTES = new AtomicLong();   // 累计字节数

private static void process(Task task) {
    // ...真实业务处理...
    FINISHED_COUNT.incrementAndGet();
    FINISHED_BYTES.addAndGet(task.payload.length);
}   // task 出了作用域，没有任何引用指向它 → 下次 GC 直接回收
```

如果业务真需要留历史记录？那就换成**有界**结构（`LinkedHashMap` 重写 `removeEldestEntry`、Guava `Cache` 设 maximumSize、或定期 `subList(0, n).clear()`），核心一句话：**集合必须有出口**。

### 7.2 复测：同样的两次 histo 对比（真实数据）

`LeakDemoFixed` 同样跑 `-Xmx64m`、同样间隔两次 `jmap -histo:live`：

| 类 | 20s 快照 | 65s 快照 | 判决 |
|---|---|---|---|
| `[B` (byte[]) | 12,862 个 / 556 KB | **12,862 个 / 556 KB（一字不差）** | 基线稳定 |
| `LeakDemoFixed$Task` | 榜上无名（top 10 开外） | 榜上无名 | 处理完即回收 ✓ |

而这 45 秒里程序处理了 **6,550 个任务、累计 12MB 流量**（程序日志可证）——流量照常走，堆纹丝不动。**同样的观测手段、相反的曲线，这就是结案证据。**

---

## 8. 排查心法总结（带走这 5 句话）

1. **泄漏的定义**：业务上的垃圾，因为引用还被持有，GC 无法回收。先想"谁还摸得到它"，再想"怎么改"。
2. **二次对比法**：单次 histo 无意义，两次对比、只看只涨不跌的业务类，`:live` 让证据变铁。
3. **dump 是快照不是录像**：它只能回答"案发那一刻谁在场"，抓的时机（半场 vs OOM）决定看到什么。
4. **Retained heap 是 MAT 的灵魂**：泄漏者的特征是 retained 巨大；从 Suspects → Dominator Tree → Path to GC Roots 三步逼近真凶。
5. **闭环**：修复后用同样的观测手段复测，曲线平了才算结案。

真实项目迁移：把 `Task` 换成 `UserSession`/`XxxRequest`/`CacheEntry`，把 `FINISHED_TASKS` 换成某个 static Map / ThreadLocal / 监控埋点的注册表，排查手法一字不改。

---

## 9. 动手清单（照抄就能跑，约 15 分钟）

前置：`export JAVA_HOME=...; export PATH=$JAVA_HOME/bin:$PATH`（本机 JDK 在 `C:/Users/Administrator/.jdks/openjdk-27.0.1.1`），以下命令都在 `jvm-lab/` 目录执行。

```bash
# ① 编译
javac -encoding UTF-8 -d target/classes src/main/java/com/jvmlab/*.java

# ② 启动泄漏程序（终端 A）
java -Xmx64m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=dumps \
     -cp target/classes com.jvmlab.LeakDemo

# ③ 另开一个终端（终端 B），找 PID
jps -l                          # 记下 com.jvmlab.LeakDemo 的 PID

# ④ 第一次 histo（终端 B）
jmap -histo:live <PID> | head -15

# ⑤ 等 60~90 秒，第二次 histo —— 对比 Task 和 [B 是不是只涨不跌
jmap -histo:live <PID> | head -15

# ⑥ 坐实后抓堆快照（终端 B）
jmap -dump:live,format=b,file=dumps/leak1.hprof <PID>

# ⑦ GUI 路线：MAT 打开 dumps/leak1.hprof → Leak Suspects Report
#    命令行路线（在 MAT 安装目录）：
ParseHeapDump.bat <绝对路径>/dumps/leak1.hprof org.eclipse.mat.api:suspects

# ⑧ 想亲眼看 OOM：终端 A 的程序换快进参数重跑，18 秒即炸并自动留 dump
java -Xmx64m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=dumps \
     -cp target/classes com.jvmlab.LeakDemo 8192 2

# ⑨ 修复版对照：跑起来重复 ③④⑤，确认数字不再涨
java -Xmx64m -cp target/classes com.jvmlab.LeakDemoFixed

# ⑩ 结束进程：taskkill //F //PID <PID>（Git Bash 双斜杠）
```

### 常见坑

| 现象 | 原因 / 解法 |
|---|---|
| `jps` 看不到目标进程 | 目标与 jps 用户/权限不同（Linux 加 sudo）；或进程已死 |
| `jmap` 报 `More than one non-option argument` | PID 变量里混了多个值（多个同名进程），先 `jps -l` 确认唯一 PID |
| 终端中文乱码 | Git Bash 显示 GBK 输出的老问题，不影响数据，可忽略 |
| dump 文件巨大 MAT 打不开 | 改 `MemoryAnalyzer.ini` 的 `-Xmx` 为 dump 大小的 1.5~2 倍 |
| `:live` 导致服务卡顿 | 它会强制 full GC；生产改低峰执行，或先抓 `jmap -histo`（无 live）粗看 |
| histogram 里 String/Class 也在涨 | 先找**你自己包名下**的类；JDK 类跟着业务类涨是连带效应 |
