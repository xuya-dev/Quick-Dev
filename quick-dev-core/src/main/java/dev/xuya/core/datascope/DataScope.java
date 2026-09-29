package dev.xuya.core.datascope;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 行级数据权限：标注在实体类上，分页/列表/统计/树/导出等所有走查询条件的接口
 * 会按 {@link DataScopeResolver} 返回的可见值集合过滤该列（如"只看本部门数据"）。
 *
 * <pre>
 * &#64;DataScope(column = "dept_id")
 * &#64;TableName("sys_user")
 * public class SysUser { ... }
 * </pre>
 *
 * <p>过滤规则：resolver 返回 null = 不限制（如管理员）；返回集合 = 追加
 * {@code column IN (集合)} 条件（空集合即查不到任何数据）。
 * 未注册 resolver 实现时不做过滤。按主键的详情/删除接口不经过查询条件，不做行级过滤。</p>
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

    /** 行级过滤的数据库列名，如 dept_id */
    String column();
}
