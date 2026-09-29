package dev.xuya.core.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 防重复提交：同一用户对同一接口在 interval 毫秒内只允许提交一次，
 * 重复请求返回业务码 400（"请勿重复提交"）。
 *
 * <pre>
 * &#64;NoRepeatSubmit(interval = 2000)
 * &#64;PostMapping("/order")
 * public R&lt;Object&gt; create(@RequestBody Order order) { ... }
 * </pre>
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoRepeatSubmit {

    /** 间隔毫秒数，默认 1000 */
    long interval() default 1000;
}
