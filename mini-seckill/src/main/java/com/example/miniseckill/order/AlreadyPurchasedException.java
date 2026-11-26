package com.example.miniseckill.order;

/**
 * 表示当前用户已有该商品订单：可能由预检查发现，也可能由数据库唯一约束发现。
 * 继承 RuntimeException，使它离开事务方法时触发默认回滚，避免重复请求多扣一件库存。
 * ApiExceptionHandler 将它转换成 HTTP 409，前端再展示构造器中的中文消息。
 */
public class AlreadyPurchasedException extends RuntimeException {
    public AlreadyPurchasedException() { super("该用户已经购买过此商品"); }
}
