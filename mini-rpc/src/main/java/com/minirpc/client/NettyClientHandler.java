package com.minirpc.client;

import com.minirpc.constant.RpcConstants;
import com.minirpc.dto.RpcResponse;
import com.minirpc.exception.RpcException;
import com.minirpc.protocol.RpcMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 收到服务端响应后,按 requestId 唤醒正在等待结果的调用线程 */
public class NettyClientHandler extends SimpleChannelInboundHandler<RpcMessage> {

    private static final Logger log = LoggerFactory.getLogger(NettyClientHandler.class);

    private final UnprocessedRequests unprocessedRequests;

    public NettyClientHandler(UnprocessedRequests unprocessedRequests) {
        this.unprocessedRequests = unprocessedRequests;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) {
        if (msg.getMessageType() != RpcConstants.RESPONSE) {
            return;
        }
        RpcResponse response = (RpcResponse) msg.getData();
        log.info("客户端收到响应: requestId={}, code={}", msg.getRequestId(), response.getCode());
        unprocessedRequests.complete(msg.getRequestId(), response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("客户端连接异常", cause);
        unprocessedRequests.failAll(cause);
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.warn("与服务端的连接已断开");
        unprocessedRequests.failAll(new RpcException("连接已断开, 调用失败"));
        ctx.fireChannelInactive();
    }
}
