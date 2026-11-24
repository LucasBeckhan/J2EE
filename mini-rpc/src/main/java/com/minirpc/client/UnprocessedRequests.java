package com.minirpc.client;

import com.minirpc.dto.RpcResponse;
import com.minirpc.exception.RpcException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在途请求表:把"异步的网络响应"对回"正在等待的调用线程"。
 *
 * 这是 RPC 框架实现"异步转同步"的关键结构:
 *   发请求时:put(requestId) 存入一个 CompletableFuture 并立刻返回
 *   收响应时:complete(requestId) 按 ID 找到 future 并回填结果
 * Dubbo 里对应的类叫 DefaultFuture,思想一模一样。
 */
public class UnprocessedRequests {

    private final Map<Long, CompletableFuture<Object>> pending = new ConcurrentHashMap<>();

    public CompletableFuture<Object> put(long requestId) {
        CompletableFuture<Object> future = new CompletableFuture<>();
        pending.put(requestId, future);
        return future;
    }

    public void complete(long requestId, RpcResponse response) {
        CompletableFuture<Object> future = pending.remove(requestId);
        if (future == null) {
            return;
        }
        if (response.getCode() == RpcResponse.SUCCESS) {
            future.complete(response.getData());
        } else {
            future.completeExceptionally(new RpcException(response.getMessage()));
        }
    }

    public void fail(long requestId, Throwable cause) {
        CompletableFuture<Object> future = pending.remove(requestId);
        if (future != null) {
            future.completeExceptionally(cause);
        }
    }

    /** 连接断开/出错时,把所有还在等待的调用全部唤醒,避免调用方永远挂着 */
    public void failAll(Throwable cause) {
        pending.forEach((id, future) -> future.completeExceptionally(cause));
        pending.clear();
    }
}
