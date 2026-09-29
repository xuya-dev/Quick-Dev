package dev.xuya.core.methodop;

import java.lang.annotation.*;

/**
 * 方法级导入：标注在 Controller 方法上，方法体无需实现，框架读取上传的 Excel
 * 并执行校验 + 事务批量插入（任一行校验失败则整体不入库）。
 *
 * <pre>
 * &#64;QuickImport(entity = Product.class, permission = "product:import")
 * &#64;PostMapping("/import")
 * public R&lt;Object&gt; importExcel(MultipartFile file) { return null; }
 * </pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface QuickImport {

    /**
     * 目标实体类
     */
    Class<?> entity();

    /**
     * 完整权限码，空串表示不鉴权
     */
    String permission() default "";
}
