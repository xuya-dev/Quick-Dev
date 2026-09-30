package dev.xuya.core.translate;

/**
 * @deprecated 拆分为 {@link DictCacheForwardResolver}（值-&gt;标签）与
 * {@link DictCacheReverseResolver}（标签-&gt;值）：单一类同时实现两个接口会导致
 * 自动装配无法按方向独立让位（类型污染）。本类仅为既有子类/调用方保留兼容，
 * 新代码请使用拆分后的实现。
 */
@Deprecated
public class DictCacheProvider implements DictResolver, DictReverseResolver {

    private final DictCacheForwardResolver forward;
    private final DictCacheReverseResolver reverse;

    public DictCacheProvider(DictCacheService cacheService) {
        this.forward = new DictCacheForwardResolver(cacheService);
        this.reverse = new DictCacheReverseResolver(cacheService);
    }

    /**
     * 值 -> 标签（读内存缓存）
     */
    @Override
    public String resolve(String dictType, Object dictValue) {
        return forward.resolve(dictType, dictValue);
    }

    /**
     * 标签 -> 值（读内存缓存）
     */
    @Override
    public Object reverse(String dictType, String label) {
        return reverse.reverse(dictType, label);
    }
}
