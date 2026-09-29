package dev.xuya.core.translate;

/**
 * 字典翻译 SPI：字典类型 + 字典值 -> 标签文本。
 * <p>实现并注册为 Bean（查字典表、读枚举、调远程字典服务均可）。
 * 未提供实现时字典翻译不可用（保留原值输出）。</p>
 */
@FunctionalInterface
public interface DictResolver {

    /**
     * @param dictType  字典类型编码，如 "user_status"（@Translate 的 dict 属性）
     * @param dictValue 字段原值，如 1
     * @return 标签文本，如 "启用"；查不到返回 null（保留原值）
     */
    String resolve(String dictType, Object dictValue);
}
