package dev.xuya.core.annotation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 核心：标注在 Controller 类上，启动时为该实体自动注册一组 CRUD 接口。
 * <p>被标注的类会成为 Spring Bean（内含 @Component），无需再写任何方法。</p>
 *
 * <pre>
 * // 零方法 CRUD：自动注册 /api/sys-user 下的 page/list/{id}/save/update/delete 接口
 * &#64;QuickCrud(entity = SysUser.class, path = "/api/sys-user", permission = "sys:user")
 * public class SysUserController {
 *     // 也可以继续写自定义接口，与生成的 CRUD 共存
 * }
 * </pre>
 *
 * <p>若不指定 {@link #path()}：优先取类上 @RequestMapping 的路径，否则按实体名推导
 * （SysUser -&gt; /sys-user，Product -&gt; /product）。</p>
 *
 * <p>权限：设置 {@link #permission()} 后，各接口需要对应权限码
 * （如 sys:user:list / sys:user:detail / sys:user:add / sys:user:edit / sys:user:remove），
 * 由 {@link dev.xuya.core.auth.PermissionChecker} 校验；不设置则接口开放（除非 loginRequired）。</p>
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface QuickCrud {

    /** 实体类（必填） */
    Class<?> entity();

    /** 对应的 BaseMapper，默认自动按泛型查找；找不到时必须显式指定 */
    Class<?> mapper() default Void.class;

    /** 接口基础路径，默认：类上 @RequestMapping 值 > 实体名推导 */
    String path() default "";

    /** 权限码前缀，空串表示不鉴权；接口级权限码 = 前缀 + ":" + 操作后缀 */
    String permission() default "";

    /** 是否要求登录（未设置 permission 时若为 true 仍要求登录） */
    boolean loginRequired() default false;

    /** 只注册这些操作，默认全部 */
    CrudOp[] includes() default {CrudOp.PAGE, CrudOp.LIST, CrudOp.COUNT, CrudOp.DETAIL,
            CrudOp.SAVE, CrudOp.UPDATE, CrudOp.REMOVE};

    /** 排除这些操作（在 includes 基础上做减法） */
    CrudOp[] excludes() default {};
}
