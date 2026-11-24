package com.minirpc.dto;

import java.io.Serializable;

/**
 * 一次远程调用请求。
 * requestId 不放在这里 —— 它写在协议头里,由 RpcMessage 统一携带,
 * 服务端响应时原样带回,客户端靠它把响应匹配回等待中的调用。
 */
public class RpcRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 服务接口全限定名,服务端据此在注册表中查找实现类 */
    private final String interfaceName;
    private final String methodName;
    private final Class<?>[] paramTypes;
    private final Object[] paramValues;

    public RpcRequest(String interfaceName, String methodName, Class<?>[] paramTypes, Object[] paramValues) {
        this.interfaceName = interfaceName;
        this.methodName = methodName;
        this.paramTypes = paramTypes;
        this.paramValues = paramValues;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public String getMethodName() {
        return methodName;
    }

    public Class<?>[] getParamTypes() {
        return paramTypes;
    }

    public Object[] getParamValues() {
        return paramValues;
    }

    @Override
    public String toString() {
        return interfaceName + "#" + methodName;
    }
}
