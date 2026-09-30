package dev.xuya.core.translate;

/**
 * 内置字典正向解析（值-&gt;标签）：基于 {@link DictCacheService} 内存缓存，不查任何数据库。
 * 数据来源见 DictLoader SPI 与导入端点。
 *
 * <p>与反向解析（{@link DictCacheReverseResolver}）拆成两个类的原因：单一类同时实现两个
 * 接口会让"用户自定义了其中一个方向"时容器里仍存在两个另一方向的 Bean（类型污染，
 * 触发 NoUniqueBeanDefinitionException）。自动装配按方向独立注册、独立让位。</p>
 */
public class DictCacheForwardResolver implements DictResolver {

    private final DictCacheService cacheService;

    public DictCacheForwardResolver(DictCacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * 值 -> 标签（读内存缓存）
     */
    @Override
    public String resolve(String dictType, Object dictValue) {
        return cacheService.getLabel(dictType, String.valueOf(dictValue));
    }
}
