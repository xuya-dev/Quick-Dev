package dev.xuya.core.methodop;

import java.lang.annotation.*;

/**
 * 方法级新增：标注在 Controller 方法上，方法体无需实现，框架自动执行 insert。
 *
 * <pre>
 * &#64;QuickSave(entity = Product.class)
 * &#64;PostMapping
 * public R&lt;Object&gt; save(@RequestBody Product product) { return null; }  // 也可声明 List&lt;Product&gt; 批量新增
 * </pre>
 * <p>权限：{@link #permission()} 为完整权限码，空串表示不鉴权。</p>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface QuickSave {

    /**
     * 目标实体类
     */
    Class<?> entity();

    /**
     * 完整权限码（如 "product:add"），空串表示不鉴权
     */
    String permission() default "";
}
