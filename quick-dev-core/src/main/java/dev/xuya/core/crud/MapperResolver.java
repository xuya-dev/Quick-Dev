package dev.xuya.core.crud;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.xuya.core.common.QuickDevException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;

import java.util.Arrays;

/**
 * 按实体类解析对应的 MyBatis-Plus Mapper Bean：
 * 显式指定优先；否则按 BaseMapper&lt;实体&gt; 泛型在容器中匹配。
 */
public final class MapperResolver {

    private static final Logger log = LoggerFactory.getLogger(MapperResolver.class);

    private MapperResolver() {
    }

    @SuppressWarnings("unchecked")
    public static BaseMapper<Object> resolve(ApplicationContext applicationContext,
                                             Class<?> entityClass, Class<?> explicitMapper) {
        if (explicitMapper != null && explicitMapper != Void.class) {
            return (BaseMapper<Object>) applicationContext.getBean(explicitMapper);
        }
        ResolvableType wanted = ResolvableType.forClassWithGenerics(BaseMapper.class, entityClass);
        String[] names = applicationContext.getBeanNamesForType(wanted);
        if (names.length == 0) {
            throw new QuickDevException("未找到 " + entityClass.getSimpleName()
                    + " 对应的 BaseMapper，请定义 XxxMapper extends BaseMapper<"
                    + entityClass.getSimpleName() + "> 并确保被扫描");
        }
        if (names.length > 1) {
            log.warn("实体 {} 匹配到多个 Mapper {}，使用第一个。可显式指定 mapper",
                    entityClass.getSimpleName(), Arrays.toString(names));
        }
        return (BaseMapper<Object>) applicationContext.getBean(names[0]);
    }
}
