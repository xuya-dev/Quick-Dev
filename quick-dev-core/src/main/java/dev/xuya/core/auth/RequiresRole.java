package dev.xuya.core.auth;

import java.lang.annotation.*;

/**
 * 声明接口所需角色（可用于任意 Controller 的方法或类上，方法优先于类）。
 * <pre>
 * &#64;RequiresRole("admin")
 * &#64;RequiresRole(value = {"admin", "manager"}, logical = Logical.OR)
 * </pre>
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRole {

    /**
     * 角色码
     */
    String[] value();

    /**
     * 多角色组合逻辑，默认全部满足
     */
    Logical logical() default Logical.AND;
}
