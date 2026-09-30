package dev.xuya.springboot.autoconfigure;

import dev.xuya.core.common.QuickDevLimits;
import dev.xuya.core.crud.EntityValidator;
import dev.xuya.core.translate.TranslateExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * core 模块静态值的装配器（core 不依赖 Spring Boot 配置绑定，静态工具无法走 Bean 注入）。
 *
 * <p>{@link SmartInitializingSingleton} 保证在所有单例 Bean 就绪后执行：
 * 无论 {@link TranslateExecutor} 是框架默认 Bean 还是用户自定义 Bean（框架默认的
 * 是 {@code @ConditionalOnMissingBean}），这里读到的都是最终生效的那一个——
 * 此前注册逻辑写在条件化 Bean 工厂方法里，用户自定义 Bean 会导致静态入口
 * 保持 null、所有 @Translate 静默失效。</p>
 */
public class QuickDevStaticConfigurer implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(QuickDevStaticConfigurer.class);

    private final QuickDevProperties properties;
    private final ObjectProvider<TranslateExecutor> executorProvider;

    public QuickDevStaticConfigurer(QuickDevProperties properties,
                                    ObjectProvider<TranslateExecutor> executorProvider) {
        this.properties = properties;
        this.executorProvider = executorProvider;
    }

    @Override
    public void afterSingletonsInstantiated() {
        QuickDevProperties.Limits limits = properties.getLimits();
        QuickDevLimits.setQueryMaxRows(limits.getQueryMaxRows());
        QuickDevLimits.setExportMaxRows(limits.getExportMaxRows());
        QuickDevLimits.setExportBatchSize(limits.getExportBatchSize());
        QuickDevLimits.setImportMaxRows(limits.getImportMaxRows());
        QuickDevLimits.setInMaxSize(limits.getInMaxSize());
        EntityValidator.setUpdateValidationEnabled(properties.getCrud().isUpdateValidate());

        TranslateExecutor executor = executorProvider.getIfAvailable();
        if (executor != null) {
            TranslateExecutor.register(executor);
        } else {
            log.debug("容器中没有 TranslateExecutor Bean，@Translate 静态入口保持未注册"
                    + "（翻译序列化与导入反解将保留原值）");
        }
    }
}
