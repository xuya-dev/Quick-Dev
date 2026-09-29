package dev.xuya.core.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明接口所需权限码（可用于任意 Controller 的方法或类上，方法优先于类）。
 * <p>多个权限码默认全部满足才放行（AND）。</p>
 * <pre>
 * &#64;RequiresPerm("sys:user:list")
 * &#64;RequiresPerm({"sys:user:add", "sys:user:edit"})
 * </pre>
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPerm {

    /** 权限码，AND 关系 */
    String[] value();
}
