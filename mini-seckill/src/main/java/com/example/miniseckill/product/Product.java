package com.example.miniseckill.product;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 商品实体：JPA 将一个 Product 对象映射为 products 表中的一行。
 * {@code @Entity} 声明实体，@Table 指定数据库表名；@Id 放在字段上，JPA 使用字段访问方式。
 *
 * 当前把商品基础信息和秒杀库存放在同一张表，没有单独的活动表或活动有效期。
 * 不能据此推导“每天重置限购”等规则：当前限购粒度是 userId + productId。
 */
@Entity
@Table(name = "products")
public class Product {
    // 数据库生成自增主键；新建对象时 id 为 null，保存后由 JPA 回填。
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // nullable/length 描述表结构约束，不等同于 HTTP 参数校验。
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 使用整数分保存金额，例如 9900 表示 99.00 元；避免用 double 做金额运算。
     * 前端 money() 只负责除以 100 并格式化展示，不参与后端价格决策。
     */
    @Column(nullable = false)
    private long priceInCents;

    /**
     * 数据库中的剩余库存，是本版的库存事实来源；没有 Redis 副本。
     * 扣减通过 Repository 的条件更新完成，不能把 getter 读出的旧值减一后覆盖回去。
     */
    @Column(nullable = false)
    private int stock;

    // JPA 加载实体时需要无参构造器；protected 满足框架要求，同时减少业务层误创建空对象。
    protected Product() { }

    // 演示数据初始化使用此构造器；当前没有管理端商品创建接口。
    public Product(String name, long priceInCents, int stock) {
        this.name = name;
        this.priceInCents = priceInCents;
        this.stock = stock;
    }

    // getter 供 Java 调用和 JSON 序列化使用；没有 setter 并不能替代数据库并发控制。
    public Long getId() { return id; }
    public String getName() { return name; }
    public long getPriceInCents() { return priceInCents; }
    public int getStock() { return stock; }
}
