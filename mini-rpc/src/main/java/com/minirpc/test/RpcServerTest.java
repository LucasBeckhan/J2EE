package com.minirpc.test;

import com.minirpc.api.HelloService;
import com.minirpc.provider.HelloServiceImpl;
import com.minirpc.server.NettyRpcServer;

/**
 * 启动 RPC 服务端(会一直阻塞监听)。
 * IDEA 里直接 Run;命令行:
 *   mvn compile exec:java -Dexec.mainClass=com.minirpc.test.RpcServerTest
 */
public class RpcServerTest {

    public static void main(String[] args) throws Exception {
        NettyRpcServer server = new NettyRpcServer();
        // 服务暴露:把实现类绑定到接口上
        server.registerService(HelloService.class, new HelloServiceImpl());
        server.start(9000);
    }
}
