package dev.xuya.springboot.autoconfigure;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.xuya.core.auth.AuthInterceptor;
import dev.xuya.core.auth.AuthSettings;
import dev.xuya.core.common.QuickDevLimits;
import dev.xuya.core.context.SpringContextHolder;
import dev.xuya.core.crud.AutoFillMetaObjectHandler;
import dev.xuya.core.crud.QuickCrudRegistrar;
import dev.xuya.core.log.AsyncOperationLogSink;
import dev.xuya.core.log.JdbcOperationLogSink;
import dev.xuya.core.log.OperationLogSink;
import dev.xuya.core.log.QuickLogAspect;
import dev.xuya.core.log.Slf4jOperationLogSink;
import dev.xuya.core.methodop.QuickOpAspect;
import dev.xuya.core.translate.TranslateExecutor;
import dev.xuya.core.web.GlobalExceptionHandler;
import dev.xuya.core.web.MemoryRepeatSubmitStore;
import dev.xuya.core.web.RepeatSubmitInterceptor;
import dev.xuya.core.web.RepeatSubmitStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Quick Dev 自动配置。
 * <ul>
 *   <li>QuickCrudRegistrar：@QuickCrud 动态端点注册（quick-dev.enabled=false 可关闭）</li>
 *   <li>MybatisPlusInterceptor：分页插件兜底（用户自定义时让位）</li>
 *   <li>AutoFillMetaObjectHandler：createTime/updateTime 自动填充（用户自定义时让位）</li>
 *   <li>AuthInterceptor：@RequiresPerm / @RequiresLogin / CRUD 权限码校验</li>
 *   <li>GlobalExceptionHandler：异常统一响应</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(QuickDevProperties.class)
public class QuickDevAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "quick-dev", name = "enabled", havingValue = "true", matchIfMissing = true)
    public QuickCrudRegistrar quickCrudRegistrar() {
        return new QuickCrudRegistrar();
    }

    @Bean
    @ConditionalOnMissingBean(MetaObjectHandler.class)
    @ConditionalOnProperty(prefix = "quick-dev.auto-fill", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public AutoFillMetaObjectHandler quickDevAutoFillMetaObjectHandler() {
        return new AutoFillMetaObjectHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = {"org.aspectj.lang.annotation.Aspect", "cn.idev.excel.FastExcel"})
    @ConditionalOnProperty(prefix = "quick-dev.method-op", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public QuickOpAspect quickOpAspect(ApplicationContext applicationContext) {
        return new QuickOpAspect(applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean(OperationLogSink.class)
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnProperty(prefix = "quick-dev.log", name = "jdbc", havingValue = "true")
    public OperationLogSink jdbcOperationLogSink(QuickDevProperties properties,
                                                 ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        return new JdbcOperationLogSink(jdbcTemplateProvider, properties.getLog().getTable());
    }

    @Bean
    @ConditionalOnMissingBean(OperationLogSink.class)
    public OperationLogSink quickDevSlf4jOperationLogSink() {
        return new Slf4jOperationLogSink();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
    @ConditionalOnProperty(prefix = "quick-dev.log", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public QuickLogAspect quickLogAspect(OperationLogSink sink, QuickDevProperties properties,
                                         ApplicationContext applicationContext) {
        ObjectMapper objectMapper = applicationContext.getBeanProvider(ObjectMapper.class)
                .getIfAvailable(ObjectMapper::new);
        OperationLogSink effective = AsyncOperationLogSink.wrap(sink, properties.getLog().isAsync());
        return new QuickLogAspect(effective, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public SpringContextHolder quickDevSpringContextHolder() {
        return new SpringContextHolder();
    }

    @Bean
    @ConditionalOnMissingBean
    public TranslateExecutor translateExecutor(QuickDevProperties properties) {
        QuickDevProperties.Translate translate = properties.getTranslate();
        TranslateExecutor executor = new TranslateExecutor(
                translate.isEnabled(), translate.getCacheSeconds() * 1000);
        TranslateExecutor.register(executor);
        return executor;
    }

    @Bean
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    public MybatisPlusInterceptor quickDevMybatisPlusInterceptor(QuickDevProperties properties) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        if (properties.getDbType() != null) {
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor(properties.getDbType()));
        } else {
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        }
        return interceptor;
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthInterceptor quickDevAuthInterceptor(QuickDevProperties properties,
                                                   ApplicationContext applicationContext) {
        QuickDevProperties.Auth auth = properties.getAuth();
        return new AuthInterceptor(
                new AuthSettings(auth.isEnabled(), auth.getTokenHeader(), auth.getTokenParam()),
                applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "quick-dev.repeat-submit", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public RepeatSubmitInterceptor repeatSubmitInterceptor(
            ObjectProvider<RepeatSubmitStore> storeProvider) {
        RepeatSubmitStore store = storeProvider.getIfAvailable(MemoryRepeatSubmitStore::new);
        return new RepeatSubmitInterceptor(store);
    }

    @Bean
    public WebMvcConfigurer quickDevAuthWebMvcConfigurer(AuthInterceptor authInterceptor,
                                                         ObjectProvider<RepeatSubmitInterceptor> repeatSubmitInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(authInterceptor).order(0).addPathPatterns("/**");
                RepeatSubmitInterceptor repeat = repeatSubmitInterceptor.getIfAvailable();
                if (repeat != null) {
                    // 排在鉴权之后：可依据 AuthContext 中的用户身份生成防重指纹
                    registry.addInterceptor(repeat).order(1).addPathPatterns("/**");
                }
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.web.bind.annotation.RestControllerAdvice")
    public GlobalExceptionHandler quickDevGlobalExceptionHandler(QuickDevProperties properties) {
        return new GlobalExceptionHandler(properties.isErrorDetail());
    }

    /** 把 quick-dev.limits.* 写入 core 静态上限（core 静态工具无法走 Bean 注入） */
    @Bean
    public String quickDevLimitsConfigurer(QuickDevProperties properties) {
        QuickDevProperties.Limits limits = properties.getLimits();
        QuickDevLimits.setExportMaxRows(limits.getExportMaxRows());
        QuickDevLimits.setImportMaxRows(limits.getImportMaxRows());
        QuickDevLimits.setInMaxSize(limits.getInMaxSize());
        return "quickDevLimitsConfigured";
    }
}
