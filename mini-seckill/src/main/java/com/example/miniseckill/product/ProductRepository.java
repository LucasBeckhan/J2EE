package com.example.miniseckill.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 商品持久化接口。{@code JpaRepository<Product, Long>} 的两个泛型分别是实体类型与主键类型。
 * Spring Data 在启动时创建代理实现，因此没有手写的 ProductRepositoryImpl。
 * findAll、existsById、save、count 等通用操作由父接口提供。
 */
public interface ProductRepository extends JpaRepository<Product, Long> {
    /**
     * 原子扣库存：数据库只会在库存仍大于 0 时执行更新。
     * 并发请求同时争抢最后一件时，只有一个请求能更新到一行。
     * 返回值为受影响行数：1 表示成功，0 表示商品不存在或已售罄。
     *
     * {@code @Query} 中是 JPQL：Product 和 stock 是实体类/属性名称，不是表名/列名。
     * Hibernate 会翻译成类似 UPDATE products SET stock = stock - 1 WHERE id = ? AND stock > 0。
     * {@code @Modifying} 告诉 Spring Data 这是更新语句；事务由外层 SeckillService 提供。
     * {@code @Param} 把 Java 参数 id 绑定到 :id，不通过字符串拼接 SQL。
     *
     * 数据库协调同一行的并发更新，执行时检查库存条件；并发冲突仍可能触发数据库异常。
     * 该批量更新绕过已加载实体的字段同步，本流程不会继续读取同一事务中的旧 Product 对象。
     */
    @Modifying
    @Query("update Product p set p.stock = p.stock - 1 where p.id = :id and p.stock > 0")
    int decreaseStockIfAvailable(@Param("id") Long id);
}
