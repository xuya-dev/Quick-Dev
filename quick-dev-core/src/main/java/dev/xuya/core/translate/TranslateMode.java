package dev.xuya.core.translate;

/**
 * {@link Translate} 的工作模式。
 *
 * <ul>
 *   <li>{@link #REPLACE}（默认）：JSON 输出时把字段值直接替换为翻译结果（原有行为）</li>
 *   <li>{@link #APPEND}：字段保留原值，翻译结果附加输出到兄弟字段
 *       （字段名由 {@link Translate#appendField()} 指定，缺省为「字段名 + Name」，如 deptId -&gt; deptIdName）。
 *       适用于编辑表单需要原始 ID、同时要展示可读文本的场景。
 *       附加字段由 {@link TranslateAppendModule}（注册为 Bean 即随 Spring Boot
 *       自动装配进 ObjectMapper）在序列化期计算，无对应翻译时输出 null。</li>
 * </ul>
 */
public enum TranslateMode {

    /**
     * 替换模式：字段值被翻译结果替换（缺省，保持 0.1.x 行为）
     */
    REPLACE,

    /**
     * 附加模式：字段保留原值，翻译结果写入兄弟字段
     */
    APPEND
}
