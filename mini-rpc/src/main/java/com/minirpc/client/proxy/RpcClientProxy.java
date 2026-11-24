package com.minirpc.client.proxy;

import com.minirpc.client.NettyRpcClient;
import com.minirpc.dto.RpcRequest;

import java.lang.reflect.Proxy;
import java.util.concurrent.TimeUnit;

/**
 * JDK 动态代理:RPC 对调用方"零感知"的秘密。
 *
 * 调用方拿到的是 HelloService 接口的代理对象,调用 hello("Netty") 时,
 * 实际执行的是下面这个 InvocationHandler:
 *   1. 把"接口名+方法名+参数类型+参数值"打包成 RpcRequest
 *   2. 发给服务端并阻塞等待结果(异步转同步)
 *   3. 把结果 return —— 调用方完全感觉不到网络的存在
 */
public class RpcClientProxy {

    /** 默认超时 15 秒:超时抛异常,防止调用方永远阻塞 */
    private static final long DEFAULT_TIMEOUT_SECONDS = 15;

    private final NettyRpcClient client;

    public RpcClientProxy(NettyRpcClient client) {
        this.client = client;
    }

    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> interfaceClass) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                (proxy, method, args) -> {
                    RpcRequest request = new RpcRequest(
                            interfaceClass.getName(),
                            method.getName(),
                            method.getParameterTypes(),
                            args);
                    return client.sendRequest(request)
                            .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                });
    }
}
