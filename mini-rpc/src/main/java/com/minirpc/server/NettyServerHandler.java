package com.minirpc.server;

import com.minirpc.constant.RpcConstants;
import com.minirpc.dto.RpcRequest;
import com.minirpc.dto.RpcResponse;
import com.minirpc.protocol.RpcMessage;
import com.minirpc.registry.ServiceRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 服务端业务 Handler:收到请求 → 查注册表 → 反射调用实现类 → 写回响应。
 *
 * 教学点(故意留的思考题):invoke() 是同步执行在 Netty 的 EventLoop 线程上的,
 * 业务一旦耗时会阻塞同一个 EventLoop 上所有连接的处理 ——
 * 真实框架(Dubbo 业务线程池)会把它丢到独立线程池,这正是扩展作业之一。
 */
public class NettyServerHandler extends SimpleChannelInboundHandler<RpcMessage> {

    private static final Logger log = LoggerFactory.getLogger(NettyServerHandler.class);

    private final ServiceRegistry registry;

    public NettyServerHandler(ServiceRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) {
        if (msg.getMessageType() != RpcConstants.REQUEST) {
            return;
        }
        RpcRequest request = (RpcRequest) msg.getData();
        log.info("服务端收到请求: {} requestId={}", request, msg.getRequestId());

        RpcResponse response = invoke(request);
        // 响应的 requestId 与请求一致,客户端才能对上号
        ctx.writeAndFlush(new RpcMessage(RpcConstants.RESPONSE, msg.getRequestId(), response));
    }

    /** 反射调用:这就是 RPC 框架最核心的那一行 —— "把网络请求翻译成本地方法调用" */
    private RpcResponse invoke(RpcRequest request) {
        try {
            Object service = registry.lookup(request.getInterfaceName());
            if (service == null) {
                return RpcResponse.fail("未找到服务: " + request.getInterfaceName());
            }
            Method method = service.getClass().getMethod(request.getMethodName(), request.getParamTypes());
            Object result = method.invoke(service, request.getParamValues());
            return RpcResponse.success(result);
        } catch (InvocationTargetException e) {
            // 业务方法内部抛的异常,取真正的 target
            return RpcResponse.fail("业务执行异常: " + e.getTargetException().getMessage());
        } catch (Exception e) {
            return RpcResponse.fail("调用失败: " + e.getMessage());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("服务端连接异常", cause);
        ctx.close();
    }
}
