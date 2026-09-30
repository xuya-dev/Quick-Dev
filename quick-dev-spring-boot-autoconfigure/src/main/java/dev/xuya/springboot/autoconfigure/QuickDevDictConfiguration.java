package dev.xuya.springboot.autoconfigure;

import dev.xuya.core.translate.DictCacheProvider;
import dev.xuya.core.translate.DictCacheService;
import dev.xuya.core.translate.DictLoader;
import dev.xuya.core.translate.DictResolver;
import dev.xuya.core.translate.DictReverseResolver;
import dev.xuya.core.translate.TranslateExecutor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 字典配置：数据全量驻留内存（{@link DictCacheService}），框架不查任何数据库。
 * 数据来源由使用方实现 {@link DictLoader} SPI 提供（远程字典服务/配置中心/自有表任选）；
 * 字典变更后调刷新端点重新 loadAll，或编程式调用 {@link DictCacheService#replaceAll} 全量替换。
 *
 * <p>类级 servlet 条件：刷新端点是 MVC Controller、属性对象由 web 门控的主配置注册，
 * 非 servlet 环境（批处理/CLI）跳过整套装配，而不是启动时 NoSuchBeanDefinitionException。</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "quick-dev.dict", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(QuickDevProperties.class)
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
     * 内置字典翻译（值 -> 标签）。与反解分开声明条件：用户只自定义其中一个方向时，
     * 另一个方向仍由内置实现兜底（此前两个方向共用一个 AND 条件，自定义反解会连正向翻译一起丢掉）。
     */
    @Bean
    @ConditionalOnMissingBean(DictResolver.class)
    public DictResolver dictCacheResolver(DictCacheService cacheService) {
        return new DictCacheProvider(cacheService);
    }

    /**
     * 内置字典反解（标签 -> 值，Excel 导入上传转换用）
     */
    @Bean
    @ConditionalOnMissingBean(DictReverseResolver.class)
    public DictReverseResolver dictCacheReverseResolver(DictCacheService cacheService) {
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
