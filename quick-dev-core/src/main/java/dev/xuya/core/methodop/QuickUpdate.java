package dev.xuya.core.methodop;

import java.lang.annotation.*;

/**
 * 方法级修改：标注在 Controller 方法上，方法体无需实现，框架自动执行 updateById
 * （null 字段不更新，实体主键必填）。
 *
 * <pre>
 * &#64;QuickUpdate(entity = Product.class)
 * &#64;PutMapping
 * public R&lt;Object&gt; update(@RequestBody Product product) { return null; }
 * </pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface QuickUpdate {

    /**
     * 目标实体类
     */
    Class<?> entity();

    /**
     * 完整权限码，空串表示不鉴权
     */
    String permission() default "";
}
