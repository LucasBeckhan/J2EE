package com.minirpc.server;

import com.minirpc.protocol.RpcMessageDecoder;
import com.minirpc.protocol.RpcMessageEncoder;
import com.minirpc.registry.ServiceRegistry;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RPC 服务端:Reactor 主从模型。
 * boss(1 个 EventLoop)只负责 accept 新连接,worker 负责已连接的读写事件。
 *
 * pipeline 从入到出:
 *   RpcMessageDecoder(切帧+反序列化) → RpcMessageEncoder(编码) → NettyServerHandler(反射调用)
 */
public class NettyRpcServer {

    private static final Logger log = LoggerFactory.getLogger(NettyRpcServer.class);

    private final ServiceRegistry registry = new ServiceRegistry();

    /** 服务暴露:provider 启动时把自己的实现注册进来 */
    public <T> void registerService(Class<T> interfaceClass, T impl) {
        registry.register(interfaceClass, impl);
    }

    /** 启动并阻塞监听(closeFuture().sync()),直到进程被杀掉 */
    public void start(int port) throws InterruptedException {
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new RpcMessageDecoder());
                            ch.pipeline().addLast(new RpcMessageEncoder());
                            ch.pipeline().addLast(new NettyServerHandler(registry));
                        }
                    });

            ChannelFuture future = bootstrap.bind(port).sync();
            log.info("RPC 服务端已启动, 监听端口: {}", port);
            future.channel().closeFuture().sync();
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }
}
