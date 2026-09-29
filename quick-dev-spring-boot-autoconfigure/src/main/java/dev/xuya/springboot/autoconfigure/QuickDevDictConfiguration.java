package dev.xuya.springboot.autoconfigure;

import dev.xuya.core.translate.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 字典配置：数据全量驻留内存（{@link DictCacheService}），框架**不查任何数据库**。
 * 数据来源三选一（可并存）：
 * <ul>
 *   <li>DictLoader SPI：用户实现 loadAll()（远程字典服务/配置中心/自有表任选）</li>
 *   <li>导入端点：POST /quick-dev/dict/import 按规定格式上传全量数据（dict:import 权限）</li>
 *   <li>刷新端点：有 loader 时 POST /quick-dev/dict/refresh 重新 loadAll（dict:refresh 权限）</li>
 * </ul>
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

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "quick-dev.dict", name = "import-endpoint-enabled",
            havingValue = "true", matchIfMissing = true)
    public QuickDictImportController quickDictImportController(DictCacheService cacheService) {
        return new QuickDictImportController(cacheService);
    }
}
