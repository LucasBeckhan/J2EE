package com.minirpc.protocol;

import com.minirpc.exception.RpcException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * JDK 自带序列化。
 * 注意:真实生产环境慎用 JDK 反序列化(有反序列化漏洞风险,且体积大),
 * 这里为了教学保持零依赖,扩展作业就是换成 Kryo/JSON。
 */
public class JdkSerializer implements Serializer {

    @Override
    public byte[] serialize(Object obj) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            oos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RpcException("序列化失败", e);
        }
    }

    @Override
    public Object deserialize(byte[] bytes) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RpcException("反序列化失败", e);
        }
    }
}
