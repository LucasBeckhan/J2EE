package com.minirpc.constant;

/**
 * 自定义协议常量。
 *
 * 协议头固定 16 字节(Dubbo 协议头也是 16 字节,同款设计):
 * <pre>
 *  0        2        3        4                12               16
 *  +--------+--------+--------+----------------+----------------+--------------+
 *  | magic  | version|  type  |    requestId   |   bodyLength   |     body     |
 *  | 2 字节 | 1 字节 | 1 字节 |     8 字节     |     4 字节     |  序列化数据  |
 *  +--------+--------+--------+----------------+----------------+--------------+
 * </pre>
 */
public final class RpcConstants {

    /** 协议魔数:收到字节流先校验它,快速识别一条合法的 mini-rpc 消息 */
    public static final int MAGIC_NUMBER = 0xCAFE;

    /** 协议版本号,为将来升级协议留的扩展位 */
    public static final byte VERSION = 1;

    /** 消息类型:调用请求 */
    public static final byte REQUEST = 1;

    /** 消息类型:调用响应 */
    public static final byte RESPONSE = 2;

    /** 协议头长度 = magic(2) + version(1) + type(1) + requestId(8) + bodyLength(4) */
    public static final int HEAD_LENGTH = 16;

    private RpcConstants() {
    }
}
