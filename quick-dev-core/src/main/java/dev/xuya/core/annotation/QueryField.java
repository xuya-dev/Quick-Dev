package dev.xuya.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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
}
