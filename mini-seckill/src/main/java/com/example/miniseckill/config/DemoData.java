package com.example.miniseckill.config;

import com.example.miniseckill.product.Product;
import com.example.miniseckill.product.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 启动演示数据初始化：Spring 应用上下文准备好后运行一次 CommandLineRunner。
 * 当前是内存数据库，每次重新启动后端进程都会重新建库并执行这份初始化。
 * count() 判断只用于“已有数据时不重复添加”；不是并发初始化锁或数据库迁移方案。
 */
@Configuration
public class DemoData {
    @Bean
    public CommandLineRunner seedProducts(ProductRepository products) {
        // @Bean 注册返回的 Runner；products 参数由 Spring 注入 Repository 代理。
        // Lambda 的 args 是应用启动参数，这里不使用；Runner 不是 HTTP 请求处理器。
        return args -> {
            if (products.count() == 0) {
                // 四种商品方便前端演示分类、搜索、单品限购和不同库存。
                // 金额单位为分；这些是真实写入数据库的数据，不是前端模拟数据。
                products.save(new Product("轻音无线头戴耳机", 9900L, 100));
                products.save(new Product("轻触机械键盘", 15900L, 50));
                products.save(new Product("日常随行保温杯", 3900L, 80));
                products.save(new Product("暖光桌面阅读灯", 6900L, 30));
            }
        };
    }
}
