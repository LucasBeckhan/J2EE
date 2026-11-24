package com.minirpc.client;

import com.minirpc.constant.RpcConstants;
import com.minirpc.dto.RpcRequest;
import com.minirpc.exception.RpcException;
import com.minirpc.protocol.RpcMessage;
import com.minirpc.protocol.RpcMessageDecoder;
import com.minirpc.protocol.RpcMessageEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RPC 客户端:负责真正把请求写进网络。
 * 懒连接:第一次调用才建立连接;连接断开后,下次调用会自动重连。
 */
public class NettyRpcClient {

    private static final Logger log = LoggerFactory.getLogger(NettyRpcClient.class);

    private final String host;
    private final int port;
    private final UnprocessedRequests unprocessedRequests = new UnprocessedRequests();
    /** 全局递增的请求 ID:并发调用时靠它把"响应"匹配回"请求" */
    private final AtomicLong REQUEST_ID = new AtomicLong();

    private volatile Channel channel;
    private volatile EventLoopGroup group;

    public NettyRpcClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * 发送请求并立刻返回 CompletableFuture。
     * 发送是异步的;真正阻塞等待发生在 RpcClientProxy 里 future.get()。
     * 若发送本身就失败,监听器会把对应 future 置为异常,调用方不会白等。
     */
    public CompletableFuture<Object> sendRequest(RpcRequest request) {
        try {
            connect();
        } catch (RpcException e) {
            CompletableFuture<Object> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }

        long requestId = REQUEST_ID.incrementAndGet();
        CompletableFuture<Object> future = unprocessedRequests.put(requestId);
        RpcMessage message = new RpcMessage(RpcConstants.REQUEST, requestId, request);
        channel.writeAndFlush(message).addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                unprocessedRequests.fail(requestId, f.cause());
            }
        });
        return future;
    }

    private synchronized void connect() {
        if (channel != null && channel.isActive()) {
            return;
        }
        if (group == null) {
            group = new NioEventLoopGroup();
        }
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new RpcMessageDecoder());
                        ch.pipeline().addLast(new RpcMessageEncoder());
                        ch.pipeline().addLast(new NettyClientHandler(unprocessedRequests));
                    }
                });
        try {
            channel = bootstrap.connect(host, port).sync().channel();
            log.info("已连接 RPC 服务端 {}:{}", host, port);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RpcException("建立连接时被中断", e);
        }
    }

    public void close() {
        if (channel != null) {
            channel.close();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
    }
}
