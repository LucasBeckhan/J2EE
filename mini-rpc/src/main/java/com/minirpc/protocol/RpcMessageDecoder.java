package com.minirpc.protocol;

import com.minirpc.constant.RpcConstants;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

/**
 * 解码器:字节流 → RpcMessage(入站方向)。
 *
 * 继承 LengthFieldBasedFrameDecoder 是 mini-rpc 的关键一课 —— TCP 粘包/拆包的解决方案:
 * 协议头里自带 bodyLength 字段,Netty 就知道"一条完整消息到底多长",
 * 一次收到半条消息会先缓存,收齐了才往下传(所谓"帧")。
 *
 * 构造参数:
 *   maxFrameLength     一条消息最大 1MB,防止恶意超长包撑爆内存
 *   lengthFieldOffset   长度字段从第 12 字节开始(前面是 magic2+version1+type1+requestId8)
 *   lengthFieldLength   长度字段本身占 4 字节
 *   lengthAdjustment=0  长度字段的值正好等于"长度字段之后剩余的字节数"(即 body)
 *   initialBytesToStrip 解码后保留完整帧(不剥头),由本类自己解析头字段
 */
public class RpcMessageDecoder extends LengthFieldBasedFrameDecoder {

    private static final int MAX_FRAME_LENGTH = 1 << 20;
    private static final Serializer SERIALIZER = new JdkSerializer();

    public RpcMessageDecoder() {
        super(MAX_FRAME_LENGTH, RpcConstants.HEAD_LENGTH - 4, 4, 0, 0);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        // super.decode 负责切帧:不够一条完整消息时返回 null,Netty 会继续攒字节
        Object decoded = super.decode(ctx, in);
        if (decoded == null) {
            return null;
        }
        ByteBuf frame = (ByteBuf) decoded;
        try {
            int magic = frame.readUnsignedShort();
            if (magic != RpcConstants.MAGIC_NUMBER) {
                throw new IllegalArgumentException("非法协议头, magic=0x" + Integer.toHexString(magic));
            }
            byte version = frame.readByte();
            if (version != RpcConstants.VERSION) {
                throw new IllegalArgumentException("协议版本不匹配, version=" + version);
            }
            byte messageType = frame.readByte();
            long requestId = frame.readLong();
            int bodyLength = frame.readInt();

            byte[] body = new byte[bodyLength];
            frame.readBytes(body);

            Object data = SERIALIZER.deserialize(body);
            return new RpcMessage(messageType, requestId, data);
        } finally {
            frame.release();
        }
    }
}
