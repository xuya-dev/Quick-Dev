package dev.xuya.core.crud;

import dev.xuya.core.common.ParamException;
import dev.xuya.core.validation.Create;
import dev.xuya.core.validation.QuickRequire;
import dev.xuya.core.validation.Update;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.groups.Default;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * 实体校验入口，按阶段区分两组语义：
 *
 * <ul>
 *   <li>{@link #validateSave}：新增——全实体 Bean Validation（{@code Default + Create} 组），
 *       叠加 {@link QuickRequire} 条件必填；</li>
 *   <li>{@link #validateUpdate}：修改——部分更新语义（null 字段视为不更新，跳过），
 *       只对<b>提交值非空</b>的字段执行约束（{@code Default + Update} 组），
 *       叠加 {@code @QuickRequire}（仅当依赖字段本次提交非空且值匹配时生效）。</li>
 * </ul>
 *
 * <p>部分更新校验可用 quick-dev.crud.update-validate=false 关闭。</p>
 */
public final class EntityValidator {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EntityValidator.class);

    private static volatile boolean updateValidationEnabled = true;
    /**
     * 校验器缺失的告警只打一次，避免每个请求刷屏
     */
    private static volatile boolean validatorMissingWarned = false;

    private EntityValidator() {
    }

    public static void setUpdateValidationEnabled(boolean enabled) {
        updateValidationEnabled = enabled;
    }

    public static boolean isUpdateValidationEnabled() {
        return updateValidationEnabled;
    }

    /**
     * 新增校验：全实体约束（Default + Create 组）+ @QuickRequire 条件必填；
     * 任一约束不满足抛出 {@link ParamException}（HTTP 400）
     */
    public static void validateSave(Object entity, Validator validator) {
        if (entity == null) {
            return;
        }
        if (validator == null) {
            warnValidatorMissing();
            return;
        }
        List<String> problems = new ArrayList<>();
        for (ConstraintViolation<Object> violation : validator.validate(entity, Default.class, Create.class)) {
            problems.add(violation.getPropertyPath() + " " + violation.getMessage());
        }
        problems.addAll(checkQuickRequire(entity, Create.class));
        if (!problems.isEmpty()) {
            throw new ParamException("参数校验失败: " + String.join("; ", problems));
        }
    }

    /**
     * 修改校验（部分更新）：仅对提交值非空的字段执行约束（Default + Update 组）
     * + @QuickRequire 条件必填；任一约束不满足抛出 {@link ParamException}（HTTP 400）
     */
    public static void validateUpdate(Object entity, Validator validator) {
        if (!updateValidationEnabled || entity == null) {
            return;
        }
        if (validator == null) {
            warnValidatorMissing();
            return;
        }
        List<String> problems = new ArrayList<>();
        for (Class<?> c = entity.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                Object value;
                try {
                    field.setAccessible(true);
                    value = field.get(entity);
                } catch (IllegalAccessException e) {
                    continue;
                }
                if (value == null) {
                    continue;
                }
                for (ConstraintViolation<Object> violation
                        : validator.validateProperty(entity, field.getName(), Default.class, Update.class)) {
                    problems.add(violation.getPropertyPath() + " " + violation.getMessage());
                }
            }
        }
        problems.addAll(checkQuickRequire(entity, Update.class));
        if (!problems.isEmpty()) {
            throw new ParamException("修改参数校验失败: " + String.join("; ", problems));
        }
    }

    /**
     * 校验器缺失时的显式告警。
     * <p>此前这里是静默 return：容器里没有 Bean Validation 实现（例如只引 core 未引
     * spring-boot-starter-validation）时，新增/修改/批量/导入的校验会 <b>100% 失效且无任何日志</b>。</p>
     */
    private static void warnValidatorMissing() {
        if (validatorMissingWarned) {
            return;
        }
        validatorMissingWarned = true;
        log.warn("容器中未找到 jakarta.validation.Validator，Bean Validation 与 @QuickRequire 校验已全部停用"
                + "（新增/修改/批量/导入均不校验）。请引入 spring-boot-starter-validation，"
                + "或确认该降级是你有意为之");
    }

    /**
     * @QuickRequire 条件必填检查（供新增/修改/Excel 导入路径共用）。
     * 修改阶段的语义：仅当依赖字段在本次提交中非空且值匹配时
     * 才要求本字段同时提交；依赖字段未提交则跳过（完整状态判断可写 CrudHook.beforeUpdate）
     */
    public static List<String> checkQuickRequire(Object entity, Class<?> phase) {
        List<String> problems = new ArrayList<>();
        for (Class<?> c = entity.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                for (QuickRequire require : field.getAnnotationsByType(QuickRequire.class)) {
                    if (!applies(require.groups(), phase)) {
                        continue;
                    }
                    Object dependValue = readField(entity, require.dependField());
                    if (dependValue == null) {
                        continue;
                    }
                    String actual = String.valueOf(dependValue);
                    for (String expected : require.dependValue()) {
                        if (expected.equals(actual)) {
                            Object value = readField(entity, field.getName());
                            boolean blank = value == null
                                    || (value instanceof String text && text.isBlank());
                            if (blank) {
                                problems.add(require.message().isEmpty()
                                        ? "当 " + require.dependField() + "=" + expected
                                        + " 时，" + field.getName() + " 不能为空"
                                        : require.message());
                            }
                            break;
                        }
                    }
                }
            }
        }
        return problems;
    }

    private static boolean applies(Class<?>[] groups, Class<?> phase) {
        for (Class<?> group : groups) {
            if (group == phase) {
                return true;
            }
        }
        return false;
    }

    private static Object readField(Object entity, String name) {
        for (Class<?> c = entity.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(entity);
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                // 向父类继续找
            }
        }
        return null;
    }
}
