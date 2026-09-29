package dev.xuya.core.log;

import java.lang.annotation.*;

/**
 * 操作日志：标注在 Controller 方法上，框架记录操作人/URI/入参/结果码/耗时/异常，
 * 交给 {@link OperationLogSink} 落地（默认 Slf4j，可自定义写库）。
 *
 * <pre>
 * &#64;QuickLog(module = "用户管理", description = "新增用户")
 * &#64;PostMapping
 * public R&lt;Object&gt; save(@RequestBody SysUser user) { ... }
 * </pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface QuickLog {

    /**
     * 业务模块
     */
    String module() default "";

    /**
     * 操作描述
     */
    String description() default "";
}
