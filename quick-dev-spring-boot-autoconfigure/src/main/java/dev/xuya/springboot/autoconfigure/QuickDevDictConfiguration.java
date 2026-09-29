package dev.xuya.springboot.autoconfigure;

import dev.xuya.core.translate.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 字典配置：数据全量驻留内存（{@link DictCacheService}），框架不查任何数据库。
 * 数据来源由使用方实现 {@link DictLoader} SPI 提供（远程字典服务/配置中心/自有表任选）；
 * 字典变更后调刷新端点重新 loadAll，或编程式调用 {@link DictCacheService#replaceAll} 全量替换。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "quick-dev.dict", name = "enabled", havingValue = "true", matchIfMissing = true)
public class QuickDevDictConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public DictCacheService dictCacheService(QuickDevProperties properties,
                                             ObjectProvider<DictLoader> loaderProvider) {
        // loader 可空：无 DictLoader 时字典数据完全来自导入端点上传
        DictCacheService cacheService = new DictCacheService(loaderProvider.getIfAvailable());
        cacheService.startAutoRefresh(properties.getDict().getRefreshIntervalSeconds());
        return cacheService;
    }

    /**
     * 单一实例同时充当 DictResolver 与 DictReverseResolver（by-type 查找保持唯一候选；
     * 用户已自定义任一方向时不注册，避免与用户实现冲突）。
     */
    @Bean
    @ConditionalOnMissingBean({DictResolver.class, DictReverseResolver.class})
    public DictCacheProvider dictCacheProvider(DictCacheService cacheService) {
        return new DictCacheProvider(cacheService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "quick-dev.dict", name = "refresh-endpoint-enabled",
            havingValue = "true", matchIfMissing = true)
    public QuickDictRefreshController quickDictRefreshController(DictCacheService cacheService,
                                                                 TranslateExecutor translateExecutor) {
        return new QuickDictRefreshController(cacheService, translateExecutor);
    }

}
