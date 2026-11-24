# mini-rpc —— 用 Netty 手写的 Dubbo 风格 RPC

一个 19 个类的教学级 RPC 框架,对应尚硅谷韩顺平 Netty 课程第 110–116 集的实战作业。
调用方像调本地方法一样调远程接口——**动态代理 + 自定义协议 + 反射调用**,Dubbo 的骨架思想全部浓缩在内。

## 运行方式(已验证跑通)

两个进程:先起服务端,再跑客户端。

**IDEA(推荐)**:把 `mini-rpc` 作为 Maven 项目导入(IDEA 会自动用自带的 Maven 下依赖),分别 Run 两个 main:

1. `com.minirpc.test.RpcServerTest` —— 启动服务端,监听 9000
2. `com.minirpc.test.RpcClientTest` —— 发起两次远程调用并打印结果

**命令行**(需自装 Maven):

```bash
mvn compile exec:java -Dexec.mainClass=com.minirpc.test.RpcServerTest   # 终端 1
mvn compile exec:java -Dexec.mainClass=com.minirpc.test.RpcClientTest   # 终端 2
```

**预期输出**(客户端):

```
[nioEventLoopGroup-2-1] INFO NettyClientHandler - 客户端收到响应: requestId=1, code=200
远程调用 hello("Netty") 的结果: 你好 [Netty]，我是服务端。执行线程: nioEventLoopGroup-3-1
[nioEventLoopGroup-2-1] INFO NettyClientHandler - 客户端收到响应: requestId=2, code=200
远程调用 sum(1, 2) 的结果: 3
```

> 注意 hello 结果里的执行线程 `nioEventLoopGroup-3-1`:这是服务端 worker EventLoop 在跑你的业务方法,直观验证 Netty 线程模型。

## 项目结构

```
mini-rpc
├── pom.xml
└── src/main/java/com/minirpc
    ├── api/            HelloService        对外服务接口(消费方只依赖它)
    ├── provider/       HelloServiceImpl    服务端真正实现
    ├── constant/       RpcConstants        协议头常量(魔数/版本/类型/头长)
    ├── exception/      RpcException        统一异常
    ├── dto/            RpcRequest          调用请求(接口名+方法名+参数类型+参数值)
    │                   RpcResponse         调用响应(code/message/data)
    ├── protocol/       RpcMessage          传输层信封:头字段 + 序列化 body
    │                   Serializer/JdkSerializer   序列化抽象与 JDK 实现(可换 Kryo)
    │                   RpcMessageEncoder    出站编码(对象→字节)
    │                   RpcMessageDecoder    入站解码(字节→对象,继承 LengthFieldBasedFrameDecoder)
    ├── registry/       ServiceRegistry     内存服务注册表(接口名→实现实例)
    ├── server/         NettyRpcServer      Reactor 主从模型启动器
    │                   NettyServerHandler  收请求→查注册表→反射调用→写响应
    ├── client/         NettyRpcClient      懒连接客户端
    │                   UnprocessedRequests 在途请求表(requestId→CompletableFuture)
    │                   NettyClientHandler  收响应→按 requestId 唤醒等待线程
    │   └── proxy/      RpcClientProxy      JDK 动态代理:接口方法→RpcRequest
    └── test/           RpcServerTest / RpcClientTest   启动入口
```

## 协议设计(16 字节头,Dubbo 协议头同款长度)

```
 0        2        3        4                12               16
 +--------+--------+--------+----------------+----------------+--------------+
 | magic  | version|  type  |    requestId   |   bodyLength   |     body     |
 | 0xCAFE |   1    | req/resp|    8 字节      |     4 字节     | JDK 序列化   |
 +--------+--------+--------+----------------+----------------+--------------+
```

- **magic**:快速识别合法消息,防串包
- **requestId**:并发下把响应匹配回请求(发 100 个并发请求,响应乱序回来也认得出谁是谁)
- **bodyLength**:粘包/拆包的解法 —— `LengthFieldBasedFrameDecoder` 按它切帧,半条消息先攒着,攒齐才处理

## 一次调用的完整链路

```
RpcClientTest
   │ proxy.getProxy(HelloService.class)  ← 拿到的是 JDK 动态代理对象
   ▼
helloService.hello("Netty")            ← 看似本地调用
   │ InvocationHandler:打包 RpcRequest(接口名/方法名/参数类型/参数值)
   ▼
NettyRpcClient.sendRequest()
   │ ① 生成自增 requestId
   │ ② UnprocessedRequests.put(requestId) → 存入 CompletableFuture
   │ ③ channel.writeAndFlush(RpcMessage)  ← 异步发出
   ▼
   ══ 网络:自定义协议帧 ══▶  服务端 pipeline
                              Decoder 切帧+反序列化
                              → NettyServerHandler
                              → ServiceRegistry.lookup("com.minirpc.api.HelloService")
                              → 反射 method.invoke(impl, args)
                              → RpcResponse.success(结果)
                              ← writeAndFlush(RESPONSE, 同一 requestId)
   ▼
NettyClientHandler.channelRead0()
   │ UnprocessedRequests.complete(requestId, response) → future.complete(data)
   ▼
RpcClientProxy 里 future.get(15s) 返回  ← 异步转同步完成
   ▼
RpcClientTest 打印结果,仿佛什么都没发生
```

## 与真实 Dubbo 的对照

| 模块 | mini-rpc | Dubbo |
|------|----------|-------|
| 协议头 | 16 字节自定义头 | 16 字节头(magic=0xDABB) |
| 注册中心 | 内存 Map | ZooKeeper / Nacos |
| 序列化 | JDK 序列化 | hessian2 / kryo / protobuf |
| 异步转同步 | UnprocessedRequests + CompletableFuture | DefaultFuture |
| 负载均衡/集群容错 | 无(单实例) | 多 provider + Failover 等策略 |
| 业务线程隔离 | 无(反射调用直接跑在 EventLoop 上) | 独立业务线程池 |

## 扩展作业(按难度递进,正好衔接并发学习计划)

1. **业务线程池隔离**:`NettyServerHandler` 里把 `invoke()` 丢进业务线程池执行(对应学习计划 W9;想想为什么 `ctx.writeAndFlush` 仍能安全回写?)
2. **心跳保活**:加 `IdleStateHandler`,`NettyClientHandler` 15 秒空闲发心跳,服务端断开死连接(课程 80 集左右有现成方案)
3. **换序列化**:实现 `KryoSerializer implements Serializer`,对比 hello 返回的报文大小
4. **多 provider + 轮询负载均衡**:客户端持有地址列表 + AtomicInteger 轮询(对应 W6 的 CAS)
5. **超时重试**:`future.get(15s)` 超时后自动重试一次(想想哪些调用不适合重试?)

## 已知取舍(教学目的,生产不可用)

- JDK 序列化有反序列化漏洞风险,真实框架必须用白名单序列化方案
- 单线程反射调用会阻塞 EventLoop(见扩展作业 1)
- 无注册中心、无鉴权、无流控 —— 这些正是"真实框架"与"玩具"的差距所在
