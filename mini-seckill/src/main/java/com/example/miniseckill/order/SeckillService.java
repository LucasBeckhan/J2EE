package com.example.miniseckill.order;

import com.example.miniseckill.product.ProductRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 秒杀写入的业务入口：参数校验 -> 查询资格 -> 条件扣库存 -> 插入订单。
 *
 * {@code @Service} 将该类交给 Spring 管理。Controller 注入的对象经过事务代理，
 * 调用公开的 createOrder 方法时，代理会开启事务、在成功时提交、在异常时决定回滚。
 * 本类本身不保存某个用户或商品的可变状态：同一 Service 实例可以被多个请求线程调用。
 * 数据安全依靠数据库条件更新、唯一约束和事务，而不是在这个单例对象上加 synchronized。
 */
@Service
public class SeckillService {
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    // 构造器注入让依赖明确可见，也便于在需要时替换成测试用的 Repository。
    public SeckillService(ProductRepository productRepository, OrderRepository orderRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    /**
     * 首版采用同步下单，便于先学清楚事务和并发控制。
     *
     * 事务中的顺序很重要：先做条件库存扣减，再插入订单；如果插入订单失败，
     * 事务回滚会同时撤销库存扣减，避免出现“扣了库存却没有订单”。
     *
     * 这里一个事务覆盖同一数据库中的两张表，不需要分布式事务。
     * {@code @Transactional} 默认针对 RuntimeException 和 Error 回滚；本项目业务异常均继承
     * RuntimeException，并且会抛出方法，不能吞掉异常后伪装成正常返回。
     * 方法返回给事务代理后才提交；saveAndFlush 成功并不意味着事务已经提交成功。
     *
     * 注意：同一对象中通过 this.createOrder() 自调用会绕过常见的 Spring 代理事务拦截。
     * 当前 Controller 通过注入的 Service 调用，因此会经过事务代理。
     *
     * @param userId 演示用户标识；当前由客户端提供，尚不是可信登录身份
     * @param productId 要购买的商品主键；每次调用购买一件
     * @return 新建订单；经事务代理成功提交后才会作为接口成功结果返回
     */
    @Transactional
    public SeckillOrder createOrder(Long userId, Long productId) {
        // 第一步：拒绝无效业务参数。HTTP 参数类型转换由 Spring 处理，正数规则由这里处理。
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId 必须是正整数");
        }

        // 第二步：先区分“不存在”和“库存为零”，让前端得到 404 与 409 两种清晰结果。
        // 该只读查询不预占商品，不提供并发互斥能力。
        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException(productId);
        }

        // 快速给出明确错误；并发下仍可能有两个请求同时通过，所以数据库唯一约束不可省略。
        // 例如同一用户的 A、B 两个请求都在订单插入前查询，它们都有可能查到 false。
        if (orderRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new AlreadyPurchasedException();
        }

        // SQL 在数据库内原子执行，不能改成“先读取 stock 再在 Java 中减一”。
        // 第三步：只有受影响行数为 1 才取得一件库存；不能只执行更新却忽略返回值。
        int updatedRows = productRepository.decreaseStockIfAvailable(productId);
        if (updatedRows == 0) {
            // 查询商品存在后仍可能已被其他请求抢完；方法抛出异常，事务不会继续创建订单。
            throw new SoldOutException();
        }

        try {
            // 唯一约束处理并发重复请求的竞态；发生异常时事务会回滚上面的库存扣减。
            // 第四步：save 把实体交给 JPA，flush 立即同步待执行写入，尽早暴露唯一约束冲突。
            // flush 不是 commit；库存与订单仍由外层同一个事务统一提交或回滚。
            return orderRepository.saveAndFlush(new SeckillOrder(userId, productId));
        } catch (DataIntegrityViolationException duplicate) {
            // 重复订单会触发数据库完整性异常；转成运行时业务异常，继续向事务代理传播。
            // 当前演示将该写入的完整性异常统一解释为重复购买。
            // DataIntegrityViolationException 也可能有其他原因；扩展约束后应进一步辨别原因，
            // 不能把这个简化处理理解成“所有数据库完整性异常都必然是重复下单”。
            throw new AlreadyPurchasedException();
        }
    }
}
