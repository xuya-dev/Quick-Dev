package dev.xuya.core.translate;

/**
 * 枚举字典：实现本接口的枚举类可直接作为 @Translate(enumClass = ...) 的字典来源，
 * 免建字典表。
 *
 * <pre>
 * public enum OrderStatus implements DictEnum {
 *     PAID(1, "已支付"), REFUNDED(2, "已退款");
 *
 *     private final int value;
 *     private final String label;
 *     // 构造/getter 略
 *
 *     &#64;Override public Object getValue() { return value; }
 *     &#64;Override public String getLabel() { return label; }
 * }
 *
 * &#64;Translate(enumClass = OrderStatus.class)
 * private Integer status;    // 1 -> "已支付"
 * </pre>
 */
public interface DictEnum {

    /**
     * 字典值（与被翻译字段匹配，按字符串比较）
     */
    Object getValue();

    /**
     * 字典标签（翻译输出）
     */
    String getLabel();
}
