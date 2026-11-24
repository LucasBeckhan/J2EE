package com.minirpc.protocol;

import com.minirpc.constant.RpcConstants;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * 编码器:RpcMessage → 字节流(出站方向)。
 * 按协议头布局依次写入:magic(2) + version(1) + type(1) + requestId(8) + bodyLength(4) + body
 */
public class RpcMessageEncoder extends MessageToByteEncoder<RpcMessage> {

    private static final Serializer SERIALIZER = new JdkSerializer();

    @Override
    protected void encode(ChannelHandlerContext ctx, RpcMessage msg, ByteBuf out) {
        byte[] body = SERIALIZER.serialize(msg.getData());

        out.writeShort(RpcConstants.MAGIC_NUMBER);   // 2 字节魔数
        out.writeByte(RpcConstants.VERSION);         // 1 字节版本
        out.writeByte(msg.getMessageType());         // 1 字节消息类型
        out.writeLong(msg.getRequestId());           // 8 字节请求 ID
        out.writeInt(body.length);                   // 4 字节 body 长度
        out.writeBytes(body);                        // 序列化的请求/响应对象
    }
}
