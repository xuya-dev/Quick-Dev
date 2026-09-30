package dev.xuya.core.translate;

/**
 * 内置字典解析器：基于 {@link DictCacheService} 内存缓存的双向解析（值-&gt;标签 / 标签-&gt;值），
 * 不查任何数据库。数据来源见 DictLoader SPI 与导入端点。
 * <p>自动装配按方向分别让位：用户自定义了 DictResolver 时正向让位、
 * 自定义了 DictReverseResolver 时反向让位，另一方向仍由本实现兜底。</p>
 */

public class DictCacheProvider implements DictResolver, DictReverseResolver {

    private final DictCacheService cacheService;

    public DictCacheProvider(DictCacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * 值 -> 标签（读内存缓存）
     */
    @Override
    public String resolve(String dictType, Object dictValue) {
        return cacheService.getLabel(dictType, String.valueOf(dictValue));
    }

    /**
     * 标签 -> 值（读内存缓存）
     */
    @Override
    public Object reverse(String dictType, String label) {
        return cacheService.getValue(dictType, label);
    }
}
