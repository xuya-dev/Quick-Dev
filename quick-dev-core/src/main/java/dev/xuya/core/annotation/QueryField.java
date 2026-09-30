package dev.xuya.core.annotation;

import java.lang.annotation.*;

/**
 * 标注在实体字段上，声明分页/列表接口中该字段对应的查询方式。
 * <pre>
 * &#64;QueryField(QueryType.LIKE)
 * private String username;   // ?username=张 -&gt; username LIKE '%张%'
 * </pre>
 * 未标注的字段默认按 {@link QueryType#EQ} 精确匹配。
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface QueryField {

    QueryType value() default QueryType.EQ;

    /**
     * LIKE 查询是否转义通配符（%、_、\）：true 时用户输入按字面量匹配，
     * 防止通配符放大匹配范围；缺省 false 保持"前端可自带通配符"的旧行为
     */
    boolean escapeWildcard() default false;
}
