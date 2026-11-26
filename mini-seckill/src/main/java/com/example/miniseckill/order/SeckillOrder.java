package com.example.miniseckill.order;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * 订单实体，映射到 seckill_orders 表；一行代表一名用户购买一个商品的一件。
 *
 * user_id + product_id 的组合唯一约束由数据库执行，覆盖单机多个线程和多个服务实例。
 * 约束粒度是“同一用户、同一商品”，没有 activity_id；当前不能表达同一商品分活动购买。
 * CREATED 只表示订单已创建，不表示付款、发货或履约成功。
 */
@Entity
@Table(name = "seckill_orders", uniqueConstraints = {
        @UniqueConstraint(name = "uk_order_user_product", columnNames = {"user_id", "product_id"})
})
public class SeckillOrder {
    // 使用数据库自增 ID；返回给前端后用作订单号展示，也用作列表渲染的唯一 key。
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 显式指定列名，便于和上方 uniqueConstraints 的数据库列名对应。
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 当前只保存商品 ID，未定义 @ManyToOne 关系，也未显式建立商品外键。
    // 因此前端展示订单名称时，需要使用商品列表按这个 ID 关联查找。
    @Column(name = "product_id", nullable = false)
    private Long productId;

    // 当前状态只有 CREATED；以后增加支付/取消时，还需要定义合法的状态迁移规则。
    @Column(nullable = false, length = 20)
    private String status;

    // LocalDateTime 不包含时区偏移；这里使用 Java 进程所在环境的本地时间。
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 供 JPA 从数据库恢复实体；业务创建订单时使用下面的有参构造器。
    protected SeckillOrder() { }

    /** 初始化一笔订单，不会自动保存；真正的数据库写入发生在 Repository.saveAndFlush。 */
    public SeckillOrder(Long userId, Long productId) {
        this.userId = userId;
        this.productId = productId;
        this.status = "CREATED";
        this.createdAt = LocalDateTime.now();
    }

    // getter 同时用于 Java 访问与 Jackson JSON 序列化；字段名对应前端 order.xxx。
    // 当前未保存成交价快照，所以订单页面只显示订单信息，不声称存在历史成交金额。
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getProductId() { return productId; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
