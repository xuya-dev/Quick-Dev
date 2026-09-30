package dev.xuya.core.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 条件必填（拦截判断）：当同对象内 <b>dependField 字段的值等于 dependValue 之一</b>时，
 * 本字段不能为空（null 或空白字符串视为空）。
 *
 * <pre>
 * public class Goods {
 *     private Integer stock;                // 库存
 *
 *     &#64;QuickRequire(dependField = "stock", dependValue = "-1",
 *             message = "stock 为 -1（下架）时必须填写原因")
 *     private String reason;                // 下架原因：条件必填
 *
 *     // 可重复：一个字段可挂多个条件
 *     &#64;QuickRequire(dependField = "type", dependValue = {"2", "3"})
 *     &#64;QuickRequire(dependField = "level", dependValue = "VIP")
 *     private String license;
 * }
 * </pre>
 *
 * <p>阶段语义：</p>
 * <ul>
 *   <li>新增（save）：全实体判断——依赖字段值匹配即要求本字段非空；</li>
 *   <li>修改（update，部分更新）：仅当 <b>dependField 在本次提交中非空且值匹配</b>时才要求
 *       本字段同时提交（dependField 未提交说明条件状态未知，交由
 *       {@code CrudHook.beforeUpdate} 结合数据库状态做完整判断）。</li>
 * </ul>
 *
 * <p>groups 含义与 Bean Validation 分组一致：阶段（新增 Default+Create / 修改
 * Default+Update）命中 groups 时注解才生效，默认两阶段都生效。</p>
 */
@Documented
@Repeatable(QuickRequire.List.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface QuickRequire {

    /**
     * 依赖字段名（同对象内的属性名）
     */
    String dependField();

    /**
     * 触发必填的依赖字段值（按字符串比较，如 "-1"、"VIP"）
     */
    String[] dependValue();

    /**
     * 生效阶段，默认新增与修改都生效
     */
    Class<?>[] groups() default {Create.class, Update.class};

    /**
     * 校验失败提示，缺省为「当 {dependField}={匹配值} 时，{字段名} 不能为空」
     */
    String message() default "";

    @Documented
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        QuickRequire[] value();
    }
}
