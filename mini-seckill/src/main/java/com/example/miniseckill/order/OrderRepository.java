package com.example.miniseckill.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 订单持久化接口；方法名中的字段必须对应 SeckillOrder 的 Java 属性。
 * Spring Data 会解析这些方法名并生成查询，不需要自己实现方法体。
 */
public interface OrderRepository extends JpaRepository<SeckillOrder, Long> {
    /**
     * 按 userId AND productId 检查订单是否存在，结果只有 true/false。
     * 这是快速检查，不是加锁或“先到先得”的保证；最终防重复依赖数据库唯一约束。
     */
    boolean existsByUserIdAndProductId(Long userId, Long productId);

    /**
     * ByUserId 表示用户筛选；OrderByCreatedAtDesc 表示按创建时间降序。
     * 返回 List 方便直接序列化成 JSON 数组；没有订单时返回空列表。
     * 目前没有分页，用户订单量增大后需要改用 Pageable 或游标分页。
     */
    List<SeckillOrder> findByUserIdOrderByCreatedAtDesc(Long userId);
}
