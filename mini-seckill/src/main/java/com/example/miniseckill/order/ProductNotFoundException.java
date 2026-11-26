package com.example.miniseckill.order;

/**
 * 商品存在性检查失败时抛出，消息附带目标 ID，便于定位请求。
 * 异常交给 ApiExceptionHandler 映射为 HTTP 404；Controller 不需要重复 try/catch。
 */
public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(Long productId) { super("商品不存在: " + productId); }
}
