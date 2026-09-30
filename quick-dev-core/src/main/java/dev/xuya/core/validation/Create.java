package dev.xuya.core.validation;

/**
 * 校验分组：新增阶段（配合 {@link Update} 使用，见 jakarta Bean Validation groups）。
 *
 * <p>用法：实体约束上声明分组，框架按阶段自动应用——</p>
 * <pre>
 * public class Goods {
 *     &#64;NotBlank(groups = Create.class)                    // 仅新增时必填
 *     private String name;
 *
 *     &#64;NotNull(groups = {Create.class, Update.class})     // 新增与修改都校验
 *     private Integer type;
 * }
 * </pre>
 * 框架行为：新增校验 {@code Default + Create} 组，修改（部分校验）校验
 * {@code Default + Update} 组；未声明 groups 的约束属于 Default 组，两阶段都生效。
 */
public interface Create {
}
