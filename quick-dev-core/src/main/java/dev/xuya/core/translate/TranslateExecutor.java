package dev.xuya.core.translate;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.xuya.core.common.QuickDevException;
import dev.xuya.core.context.SpringContextHolder;
import dev.xuya.core.crud.EntityMeta;
import dev.xuya.core.crud.MapperResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.support.DefaultConversionService;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 翻译执行器：按 @Translate 声明分派枚举/字典/关联三种模式，结果带 TTL 本地缓存；
 * 并提供反向解析（{@link #reverse}，标签 -> 值）供 Excel 导入等上传转换场景使用。
 * <p>由自动配置注册为单例 Bean；Jackson 序列化器经 {@link #getInstance()} 静态获取，
 * 非容器环境（单测直接序列化）返回 null 时保留原值。</p>
 */
public class TranslateExecutor {

    private static final Logger log = LoggerFactory.getLogger(TranslateExecutor.class);

    private static final int CACHE_LIMIT = 8192;
    private static volatile TranslateExecutor instance;

    private final boolean enabled;
    /** 缓存毫秒数，<=0 表示禁用 */
    private final long cacheMillis;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public TranslateExecutor(boolean enabled, long cacheMillis) {
        this.enabled = enabled;
        this.cacheMillis = cacheMillis;
    }

    public static TranslateExecutor getInstance() {
        return instance;
    }

    /** 供自动配置在 Bean 创建时登记单例 */
    public static void register(TranslateExecutor executor) {
        instance = executor;
    }

    /** 清空翻译结果缓存（字典刷新等场景调用，保证新数据立即生效） */
    public void clearCache() {
        cache.clear();
    }

    /** @return 翻译结果；null 表示不翻译（保留原值输出） */
    public String translate(Translate annotation, Object value) {
        if (!enabled || annotation == null || value == null) {
            return null;
        }
        try {
            String cacheKey = cacheKey(annotation, value);
            CacheEntry cached = cache.get(cacheKey);
            long now = System.currentTimeMillis();
            if (cached != null && (cacheMillis <= 0 || now - cached.at < cacheMillis)) {
                return cached.value;
            }
            String result = doTranslate(annotation, value);
            if (cache.size() > CACHE_LIMIT) {
                cache.clear();
            }
            cache.put(cacheKey, new CacheEntry(result, now));
            return result;
        } catch (Exception e) {
            log.debug("字段翻译失败, 保留原值: {}", e.getMessage());
            return null;
        }
    }

    private String doTranslate(Translate annotation, Object value) {
        if (annotation.enumClass() != Void.class) {
            return translateByEnum(annotation.enumClass(), value);
        }
        if (!annotation.dict().isEmpty()) {
            DictResolver resolver = SpringContextHolder.getBeanIfAvailable(DictResolver.class);
            return resolver == null ? null : resolver.resolve(annotation.dict(), value);
        }
        if (annotation.entity() != Void.class) {
            Class<?> entityClass = annotation.entity();
            EntityMeta meta = EntityMeta.of(entityClass);
            BaseMapper<Object> mapper = MapperResolver.resolve(
                    SpringContextHolder.getContext(), entityClass, Void.class);
            Object id = convertId(value, meta);
            Object target = null;
            if (id instanceof java.io.Serializable serializable) {
                target = mapper.selectById(serializable);
            }
            if (target == null) {
                return null;
            }
            Field field = resolveField(entityClass, annotation.field());
            field.setAccessible(true);
            try {
                Object translated = field.get(target);
                return translated == null ? null : String.valueOf(translated);
            } catch (IllegalAccessException e) {
                throw new QuickDevException("读取翻译属性失败: " + e.getMessage(), e);
            }
        }
        return null;
    }

    /**
     * 反向解析（上传转换）：标签 -> 值。用于 Excel 导入等场景。
     * <ul>
     *   <li>枚举模式：自动按 DictEnum.getLabel() 匹配返回 getValue()</li>
     *   <li>字典模式：DictReverseResolver SPI（用户自主实现）</li>
     *   <li>关联模式：按目标属性值反查主键（多条取第一条）</li>
     * </ul>
     *
     * @return 反解出的值；null 表示无法反解（调用方保留原值）
     */
    public Object reverse(Translate annotation, String label) {
        if (!enabled || annotation == null || label == null) {
            return null;
        }
        try {
            // 缓存值统一字符串化即可，调用方（Excel 导入）随后会做字段类型转换
            String cacheKey = "rev:" + cacheKey(annotation, label);
            CacheEntry cached = cache.get(cacheKey);
            long now = System.currentTimeMillis();
            if (cached != null && (cacheMillis <= 0 || now - cached.at < cacheMillis)) {
                return cached.value;
            }
            Object result = doReverse(annotation, label);
            if (cache.size() > CACHE_LIMIT) {
                cache.clear();
            }
            cache.put(cacheKey, new CacheEntry(result == null ? null : String.valueOf(result), now));
            return result;
        } catch (Exception e) {
            log.debug("字典反解失败, 保留原值[{}]: {}", label, e.getMessage());
            return null;
        }
    }

    private Object doReverse(Translate annotation, String label) {
        if (annotation.enumClass() != Void.class) {
            return reverseByEnum(annotation.enumClass(), label);
        }
        if (!annotation.dict().isEmpty()) {
            DictReverseResolver resolver = SpringContextHolder.getBeanIfAvailable(DictReverseResolver.class);
            return resolver == null ? null : resolver.reverse(annotation.dict(), label);
        }
        if (annotation.entity() != Void.class) {
            return reverseByRef(annotation, label);
        }
        return null;
    }

    private Object reverseByEnum(Class<?> enumClass, String label) {
        if (!enumClass.isEnum() || !DictEnum.class.isAssignableFrom(enumClass)) {
            return null;
        }
        for (Object constant : enumClass.getEnumConstants()) {
            DictEnum dictEnum = (DictEnum) constant;
            if (label.equals(dictEnum.getLabel())) {
                return dictEnum.getValue();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object reverseByRef(Translate annotation, String label) {
        Class<?> entityClass = annotation.entity();
        EntityMeta meta = EntityMeta.of(entityClass);
        Field field = resolveField(entityClass, annotation.field());
        String column = meta.getColumn(annotation.field());
        if (column == null) {
            column = EntityMeta.camelToSnake(annotation.field());
        }
        BaseMapper<Object> mapper = MapperResolver.resolve(
                SpringContextHolder.getContext(), entityClass, Void.class);
        List<Object> matched = mapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Object>()
                        .eq(column, label)
                        .last("limit 1"));
        if (matched == null || matched.isEmpty()) {
            return null;
        }
        try {
            field.setAccessible(true);
            return meta.getIdField().get(matched.get(0));
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private Field resolveField(Class<?> entityClass, String name) {
        EntityMeta meta = EntityMeta.of(entityClass);
        Field field = meta.getField(name);
        if (field == null) {
            field = findDeclaredField(entityClass, name);
        }
        if (field == null) {
            throw new QuickDevException(entityClass.getSimpleName() + " 不存在属性 " + name);
        }
        return field;
    }

    /** 枚举字典翻译：值与 DictEnum.getValue() 按字符串比较 */
    private String translateByEnum(Class<?> enumClass, Object value) {
        if (!enumClass.isEnum() || !DictEnum.class.isAssignableFrom(enumClass)) {
            throw new QuickDevException(enumClass.getSimpleName()
                    + " 不是实现 DictEnum 的枚举，无法用于 @Translate(enumClass=...)");
        }
        for (Object constant : enumClass.getEnumConstants()) {
            DictEnum dictEnum = (DictEnum) constant;
            if (dictEnum.getValue() != null
                    && String.valueOf(dictEnum.getValue()).equals(String.valueOf(value))) {
                return dictEnum.getLabel();
            }
        }
        return null;
    }

    /** 字段值 -> 目标实体主键类型（如 "1" -> 1L），失败用原值 */
    private Object convertId(Object value, EntityMeta meta) {
        try {
            return new DefaultConversionService().convert(value, meta.getIdType());
        } catch (Exception e) {
            return value;
        }
    }

    private Field findDeclaredField(Class<?> type, String name) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // 继续向父类找
            }
        }
        return null;
    }

    private String cacheKey(Translate annotation, Object value) {
        if (annotation.enumClass() != Void.class) {
            return "enum:" + annotation.enumClass().getName() + ':' + value;
        }
        if (!annotation.dict().isEmpty()) {
            return "dict:" + annotation.dict() + ':' + value;
        }
        return "ref:" + annotation.entity().getName() + ':' + annotation.field() + ':' + value;
    }

    private record CacheEntry(String value, long at) {
    }
}
