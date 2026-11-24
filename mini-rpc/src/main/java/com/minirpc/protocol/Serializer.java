package com.minirpc.protocol;

/**
 * 序列化抽象 —— 框架最重要的扩展点之一。
 * 本项目默认实现是 JDK 序列化(最简单,学习用);
 * 真实框架会换成 hessian2 / kryo / protobuf(体积小、跨语言、更安全)。
 */
public interface Serializer {

    byte[] serialize(Object obj);

    Object deserialize(byte[] bytes);
}
