package dev.xuya.core.translate;

/**
 * 字典反解 SPI：标签 -> 值（Excel 导入等"上传转换"场景）。
 * <p>实现并注册为 Bean：用户上传的 Excel 里填的是中文标签（如"启用"），
 * 框架导入时调用本接口反解回库存值（如 1）。</p>
 *
 * <p>说明：@Translate(enumClass = ...) 枚举模式与 @Translate(entity = ...) 关联模式
 * 由框架自动反解，无需实现本接口；仅 @Translate(dict = "...") 字典模式需要。</p>
 */
@FunctionalInterface
public interface DictReverseResolver {

    /**
     * @param dictType 字典类型编码（@Translate 的 dict 属性）
     * @param label    标签文本，如 "启用"
     * @return 对应的字典值（如 1）；无法反解返回 null（保留原文，导入时可能因类型不符报错）
     */
    Object reverse(String dictType, String label);
}
