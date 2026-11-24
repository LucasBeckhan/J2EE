package com.minirpc.api;

/**
 * 服务提供方暴露的接口,消费方只依赖这个接口 —— 这就是 Dubbo 的"面向接口编程"。
 * 接口和实现分开放:api 包(接口)可以单独打 jar 发给消费方,实现留在服务端。
 */
public interface HelloService {

    String hello(String name);

    int sum(int a, int b);
}
