package minispring;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 迷你 IoC 容器，三步走：
 * 1. 读配置 —— 从 properties 拿到类名列表（运行时字符串）
 * 2. 实例化 —— 反射调用无参构造器，存入单例池 Map
 * 3. 依赖注入 —— 扫描 @MyAutowired 字段，从单例池取值、反射赋值
 */
public class MiniApplicationContext {

    /** 单例池：Class -> 实例。Spring 里这个 Map 叫 singletonObjects */
    private final Map<Class<?>, Object> beans = new HashMap<>();

    public MiniApplicationContext(String propertiesFile) throws Exception {
        // ── 第 1 步：读取类名列表 ──
        Properties props = new Properties();
        InputStream in = getClass().getClassLoader().getResourceAsStream(propertiesFile);
        if (in == null) {
            throw new IllegalArgumentException("找不到配置文件: " + propertiesFile);
        }
        props.load(in);

        List<Class<?>> classes = new ArrayList<>();
        for (Object value : props.values()) {
            // Class.forName：类名是字符串，编译期根本不知道是谁 —— 反射的意义所在
            classes.add(Class.forName((String) value));
        }

        // ── 第 2 步：反射实例化（要求有无参构造器）──
        for (Class<?> clazz : classes) {
            Object instance = clazz.getDeclaredConstructor().newInstance();
            beans.put(clazz, instance);
        }

        // ── 第 3 步：扫描 @MyAutowired 字段，完成注入 ──
        for (Object bean : beans.values()) {
            for (Field field : bean.getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(MyAutowired.class)) {
                    Object dependency = beans.get(field.getType()); // 按类型查找依赖
                    if (dependency == null) {
                        throw new IllegalStateException(
                                "容器里没有 " + field.getType().getName() + " 类型的 Bean，无法注入");
                    }
                    field.setAccessible(true);          // 字段是 private 的，关闭访问检查
                    field.set(bean, dependency);        // 等价于 bean.field = dependency
                }
            }
        }
    }

    /** 按类型取 Bean。type.cast 保留了泛型信息，调用方不用强转 */
    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> type) {
        return (T) beans.get(type);
    }
}
