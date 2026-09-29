package dev.xuya.core.auth;

import java.lang.annotation.*;

/**
 * 标记接口需要登录（可用于任意 Controller 的方法或类上）。
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresLogin {
}
