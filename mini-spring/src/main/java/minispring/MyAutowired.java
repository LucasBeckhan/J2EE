package minispring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记在字段上，表示该字段由容器自动注入。
 *
 * RUNTIME：注解信息保留到运行时，反射才能读到它（SOURCE/CLASS 都读不到）
 * FIELD： 只允许标注在字段上
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MyAutowired {
}
