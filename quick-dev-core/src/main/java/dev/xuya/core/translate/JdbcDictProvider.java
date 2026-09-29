package dev.xuya.core.translate;

/**
 * 内置数据库字典：基于 {@link DictCacheService} 内存缓存的双向解析，
 * 翻译/反解不查库。字典表默认约定 sys_dict(dict_type, dict_value, dict_label)，
 * 零代码接入：
 *
 * <pre>
 * quick-dev:
 *   dict:
 *     enabled: true       # classpath 有 JdbcTemplate 时自动生效
 *     table: sys_dict
 *     type-column: dict_type
 *     value-column: dict_value
 *     label-column: dict_label
 * </pre>
 *
 * <p>缓存首次访问自动加载，字典数据变更后调用刷新接口
 * （默认 POST /quick-dev/dict/refresh，需 dict:refresh 权限）。</p>
 */
public class JdbcDictProvider implements DictResolver, DictReverseResolver {

    private final DictCacheService cacheService;

    public JdbcDictProvider(DictCacheService cacheService) {
        this.cacheService = cacheService;
    }

    /** 值 -> 标签（读内存缓存） */
    @Override
    public String resolve(String dictType, Object dictValue) {
        return cacheService.getLabel(dictType, String.valueOf(dictValue));
    }

    /** 标签 -> 值（读内存缓存） */
    @Override
    public Object reverse(String dictType, String label) {
        return cacheService.getValue(dictType, label);
    }
}
