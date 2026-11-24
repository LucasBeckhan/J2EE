package com.minirpc.registry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 最简版服务注册中心:一个内存 Map。
 * key = 接口全限定名, value = 实现类实例。
 * 真实 Dubbo 会用 ZooKeeper/Nacos:provider 启动时把地址注册上去,
 * consumer 订阅后拿到地址列表,再做负载均衡。这是后面最重要的扩展作业。
 */
public class ServiceRegistry {

    private final Map<String, Object> services = new ConcurrentHashMap<>();

    public <T> void register(Class<T> interfaceClass, T impl) {
        services.put(interfaceClass.getName(), impl);
    }

    public Object lookup(String interfaceName) {
        return services.get(interfaceName);
    }
}
