package com.minirpc.dto;

import java.io.Serializable;

/** 一次远程调用的响应:code=200 表示成功,data 是结果;否则 message 携带失败原因 */
public class RpcResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int SUCCESS = 200;

    private final int code;
    private final String message;
    private final Object data;

    private RpcResponse(int code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static RpcResponse success(Object data) {
        return new RpcResponse(SUCCESS, "ok", data);
    }

    public static RpcResponse fail(String message) {
        return new RpcResponse(500, message, null);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }
}
