package dev.xuya.core.annotation;

/**
 * 查询条件类型，配合 {@link QueryField} 标注在实体字段上，
 * 列表/分页接口会将同名请求参数按该类型拼接为查询条件。
 */
public enum QueryType {

    /**
     * 等值 =
     */
    EQ,
    /**
     * 不等 !=
     */
    NE,
    /**
     * 模糊 LIKE '%v%'
     */
    LIKE,
    /**
     * 大于 &gt;
     */
    GT,
    /**
     * 大于等于 &gt;=
     */
    GE,
    /**
     * 小于 &lt;
     */
    LT,
    /**
     * 小于等于 &lt;=
     */
    LE,
    /**
     * 包含 IN (v1,v2,...)，参数值用英文逗号分隔
     */
    IN,
    /**
     * 闭区间 BETWEEN v1 AND v2，参数值为 v1,v2（英文逗号分隔，常用于时间范围）
     */
    BETWEEN
}
