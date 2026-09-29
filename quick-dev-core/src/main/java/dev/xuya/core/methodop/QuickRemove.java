package dev.xuya.core.methodop;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级删除：标注在 Controller 方法上，方法体无需实现，框架自动执行删除（支持批量）。
 *
 * <pre>
 * &#64;QuickRemove(entity = Product.class)
 * &#64;DeleteMapping("/{ids}")
 * public R&lt;Object&gt; remove(@PathVariable("ids") String ids) { return null; }  // "1" 或 "1,2,3"；也可声明 List 类型参数
 * </pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface QuickRemove {

    /** 目标实体类 */
    Class<?> entity();

    /** 完整权限码，空串表示不鉴权 */
    String permission() default "";
}
