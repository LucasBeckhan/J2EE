package com.minirpc.protocol;

/**
 * 传输层"信封":协议头字段 + 业务数据。
 * 头部字段由 Encoder/Decoder 手工按字节读写;data 会被整体序列化进 body
 * (请求方向是 RpcRequest,响应方向是 RpcResponse,都要求可序列化)。
 */
public class RpcMessage {

    private final byte messageType;
    private final long requestId;
    private final Object data;

    public RpcMessage(byte messageType, long requestId, Object data) {
        this.messageType = messageType;
        this.requestId = requestId;
        this.data = data;
    }

    public byte getMessageType() {
        return messageType;
    }

    public long getRequestId() {
        return requestId;
    }

    public Object getData() {
        return data;
    }
}
