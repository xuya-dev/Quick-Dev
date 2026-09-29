package dev.xuya.core.methodop;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级导出：标注在 Controller 方法上，方法体无需实现，框架按当前请求参数
 * （与 page 接口同一套 @QueryField 条件规则）查询并以 Excel 附件下载。
 *
 * <pre>
 * &#64;QuickExport(entity = Product.class)
 * &#64;GetMapping("/export")
 * public void export(HttpServletResponse response) { }
 * </pre>
 * <p>列名：实体字段加 FastExcel 的 @ExcelProperty("中文名")，未加则按字段名导出。</p>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface QuickExport {

    /** 目标实体类 */
    Class<?> entity();

    /** 完整权限码，空串表示不鉴权 */
    String permission() default "";
}
