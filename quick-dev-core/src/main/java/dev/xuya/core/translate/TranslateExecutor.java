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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 翻译执行器：按 @Translate 声明分派字典/关联两种模式，结果带 TTL 本地缓存。
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
            Field field = meta.getField(annotation.field());
            if (field == null) {
                field = findDeclaredField(entityClass, annotation.field());
            }
            if (field == null) {
                throw new QuickDevException(entityClass.getSimpleName() + " 不存在属性 " + annotation.field());
            }
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
