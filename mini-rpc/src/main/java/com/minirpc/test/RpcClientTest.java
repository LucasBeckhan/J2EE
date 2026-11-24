package com.minirpc.test;

import com.minirpc.api.HelloService;
import com.minirpc.client.NettyRpcClient;
import com.minirpc.client.proxy.RpcClientProxy;

/**
 * 发起远程调用。
 * IDEA 里直接 Run(需先启动 RpcServerTest);命令行:
 *   mvn compile exec:java -Dexec.mainClass=com.minirpc.test.RpcClientTest
 */
public class RpcClientTest {

    public static void main(String[] args) throws Exception {
        NettyRpcClient client = new NettyRpcClient("127.0.0.1", 9000);
        RpcClientProxy proxy = new RpcClientProxy(client);

        // 拿到的其实是一个代理对象,但用起来和本地方法一模一样
        HelloService helloService = proxy.getProxy(HelloService.class);

        String result = helloService.hello("Netty");
        System.out.println("远程调用 hello(\"Netty\") 的结果: " + result);

        int sum = helloService.sum(1, 2);
        System.out.println("远程调用 sum(1, 2) 的结果: " + sum);

        client.close();
        System.out.println("客户端调用完毕, 已关闭连接。");
    }
}
