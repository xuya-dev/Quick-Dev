package dev.xuya.core.translate;

/**
 * 内置字典反向解析（标签-&gt;值，Excel 导入上传转换用）：
 * 基于 {@link DictCacheService} 内存缓存，不查任何数据库。
 *
 * <p>与正向解析（{@link DictCacheForwardResolver}）拆成两个类，保证自动装配可以
 * 按方向独立注册、独立让位，见 {@link DictCacheForwardResolver} 类注释。</p>
 */
public class DictCacheReverseResolver implements DictReverseResolver {

    private final DictCacheService cacheService;

    public DictCacheReverseResolver(DictCacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * 标签 -> 值（读内存缓存）
     */
    @Override
    public Object reverse(String dictType, String label) {
        return cacheService.getValue(dictType, label);
    }
}
