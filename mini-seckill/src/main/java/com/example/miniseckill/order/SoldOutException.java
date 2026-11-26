package com.example.miniseckill.order;

/**
 * 条件扣库存未更新到任何行时抛出，表示本次请求未获得库存。
 * 它是运行时异常，会中断事务；异常处理器映射成 HTTP 409，而不是创建空订单。
 */
public class SoldOutException extends RuntimeException {
    public SoldOutException() { super("商品已售罄"); }
}
