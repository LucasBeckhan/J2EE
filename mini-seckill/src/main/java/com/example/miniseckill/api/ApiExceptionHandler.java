package com.example.miniseckill.api;

import com.example.miniseckill.order.AlreadyPurchasedException;
import com.example.miniseckill.order.ProductNotFoundException;
import com.example.miniseckill.order.SoldOutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一异常出口：Controller 调用失败时，将特定异常映射成 HTTP 状态和 JSON 错误体。
 *
 * {@code @RestControllerAdvice} 作用于 Controller 请求处理链；它不负责回滚事务。
 * 回滚发生在 Service 的事务代理处，随后异常继续向外传播到这里。
 * 前端 client.js 读取响应中的 message 展示中文提示，读取 HTTP 状态区分业务冲突与服务故障。
 * 本类只处理列出的异常；其他系统异常仍由 Spring Boot 的默认错误处理机制处理。
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    /** 商品不存在属于资源不存在，映射为 404，不与“已售罄”混用。 */
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ProductNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** 请求格式正确，但当前业务状态不允许购买，映射为 409 Conflict。 */
    @ExceptionHandler({AlreadyPurchasedException.class, SoldOutException.class})
    public ResponseEntity<ApiError> handleConflict(RuntimeException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** 这里处理 Service 显式抛出的参数错误；并不覆盖所有 Spring 参数绑定异常。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // ResponseEntity 设置真实 HTTP 状态；body 中的 status 字段只是相同信息的 JSON 表达。
    private ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), message));
    }

    /** 错误响应 DTO，例如 {"status":409,"message":"商品已售罄"}。 */
    public static class ApiError {
        private final int status;
        private final String message;
        public ApiError(int status, String message) { this.status = status; this.message = message; }
        // Jackson 根据公开 getter 序列化属性，无需手动拼接 JSON 字符串。
        public int getStatus() { return status; }
        public String getMessage() { return message; }
    }
}
