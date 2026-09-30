package dev.xuya.core.validation;

/**
 * 校验分组：修改阶段（配合 {@link Create} 使用，见 jakarta Bean Validation groups）。
 * 修改为部分更新语义（null 不更新），框架只对提交值非空的字段应用该组约束。
 *
 * <pre>
 * &#64;Size(min = 2, max = 30, groups = Update.class)      // 仅修改时校验长度
 * private String remark;
 * </pre>
 */
public interface Update {
}
