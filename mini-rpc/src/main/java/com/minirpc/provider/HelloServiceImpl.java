package com.minirpc.provider;

import com.minirpc.api.HelloService;

/** 服务端真正的业务实现 */
public class HelloServiceImpl implements HelloService {

    @Override
    public String hello(String name) {
        // 打印执行线程名:你会看到是 Netty 的 EventLoop 线程在跑业务方法(教学点)
        return "你好 [" + name + "]，我是服务端。执行线程: " + Thread.currentThread().getName();
    }

    @Override
    public int sum(int a, int b) {
        return a + b;
    }
}
