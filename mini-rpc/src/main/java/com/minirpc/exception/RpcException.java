package com.minirpc.exception;

/** RPC 框架统一异常:网络失败、超时、服务端业务异常都会以它抛给调用方 */
public class RpcException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RpcException(String message) {
        super(message);
    }

    public RpcException(String message, Throwable cause) {
        super(message, cause);
    }
}
