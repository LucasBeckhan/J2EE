package com.example.miniseckill.api;

import com.example.miniseckill.order.OrderRepository;
import com.example.miniseckill.order.SeckillOrder;
import com.example.miniseckill.order.SeckillService;
import com.example.miniseckill.product.Product;
import com.example.miniseckill.product.ProductRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HTTP 接入层：把浏览器请求转换成 Java 方法调用，再将返回对象序列化成 JSON。
 *
 * 调用链：App.vue -> client.js -> Vite /api 代理 -> 本 Controller -> Service/Repository。
 * {@code @RestController} 表示方法返回响应数据，不返回 JSP 或 HTML 模板名称。
 * 类上的 /api 与方法上的路径拼接，形成最终接口地址。
 *
 * 简单的只读查询直接调用 Repository；涉及多步写入的抢购交给 Service 组织事务，
 * 避免 Controller 自己拼接扣库存、写订单而遗漏事务边界。
 */
@RestController
@RequestMapping("/api")
public class SeckillController {
    // final 表示依赖在构造后不能被重新赋值，并不等于 Repository 内部完全没有状态。
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final SeckillService seckillService;

    // 单构造器会被 Spring 用于依赖注入，无需手写 new，也无需额外标注 @Autowired。
    public SeckillController(ProductRepository productRepository, OrderRepository orderRepository,
                             SeckillService seckillService) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.seckillService = seckillService;
    }

    /**
     * GET /api/products：返回商品数组；即使没有商品，也会得到 []，而不是 null。
     * findAll 是 JpaRepository 提供的方法，当前没有分页和显式排序，适用于少量演示数据。
     * 返回的库存是查询时刻的快照；别的用户随后可能买走最后一件，不能据此保证下单成功。
     * 此处直接返回实体便于学习；更复杂的项目通常用 DTO 明确控制暴露的字段。
     */
    @GetMapping("/products")
    public List<Product> products() {
        return productRepository.findAll();
    }

    /**
     * 发起秒杀。为了便于本地学习，用户 ID 通过参数传入；生产环境应从登录态中识别用户，
     * 不能相信客户端任意指定的 userId。
     *
     * 例如 POST /api/products/1/seckill?userId=1001：
     * {@code @PathVariable} 从路径取 productId，@RequestParam 从查询参数取 userId，
     * Spring 将文本数字转换为 Long；下单修改数据，因此使用 POST 而非 GET。
     * 本接口同步返回订单，默认成功状态为 200；没有异步“排队中”协议。
     * Service 抛出的业务异常交给 ApiExceptionHandler 转成 400/404/409。
     */
    @PostMapping("/products/{productId}/seckill")
    public SeckillOrder seckill(@PathVariable Long productId, @RequestParam Long userId) {
        return seckillService.createOrder(userId, productId);
    }

    /**
     * GET /api/users/1001/orders：按用户 ID 查询订单，按创建时间倒序返回。
     * 前端用返回结果生成“已抢购”的商品集合，而不是依靠浏览器内存猜测是否购买。
     * 当前只按 userId 筛选，没有认证授权；生产环境必须从可信登录态识别用户并校验权限。
     */
    @GetMapping("/users/{userId}/orders")
    public List<SeckillOrder> orders(@PathVariable Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
