package com.example.miniseckill;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 后端启动入口：先读配置、组装 Spring 容器，再启动内嵌 Tomcat 处理 HTTP 请求。
 *
 * {@code @SpringBootApplication} 集成了配置声明、自动配置和组件扫描。
 * 扫描默认从当前类的包 com.example.miniseckill 向下进行，因此 Controller、Service、
 * Repository 和 Configuration 都放在其子包中，不需要在 main 方法中逐个 new。
 *
 * 这里的 main 只负责启动；实际抢购发生在请求到来之后，由 Controller 调用 Service。
 */
@SpringBootApplication
public class MiniSeckillApplication {
    public static void main(String[] args) {
        // args 可传入 --server.port=8081 等配置覆盖项；默认配置见 application.yml。
        SpringApplication.run(MiniSeckillApplication.class, args);
    }
}
