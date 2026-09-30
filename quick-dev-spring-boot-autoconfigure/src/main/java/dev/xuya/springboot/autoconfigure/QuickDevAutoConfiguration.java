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
import dev.xuya.core.crud.EntityValidator;
import dev.xuya.core.crud.QuickCrudRegistrar;
import dev.xuya.core.log.OperationLogSink;
import dev.xuya.core.log.QuickLogAspect;
import dev.xuya.core.log.Slf4jOperationLogSink;
import dev.xuya.core.methodop.QuickOpAspect;
import dev.xuya.core.translate.TranslateAppendModule;
import dev.xuya.core.translate.TranslateExecutor;
import dev.xuya.core.web.GlobalExceptionHandler;
import dev.xuya.core.web.MemoryRepeatSubmitStore;
import dev.xuya.core.web.RepeatSubmitInterceptor;
import dev.xuya.core.web.RepeatSubmitStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
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
 *
 * <p>类级 {@code @ConditionalOnClass} 防御 MyBatis-Plus 缺失：本模块的 pom 把 MP 声明为
 * optional，且 {@link QuickDevProperties} 的 dbType 绑定也依赖 MP 类型，无 MP 时整个
 * 框架本就无法工作，直接跳过装配而不是抛 NoClassDefFoundError。</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({MetaObjectHandler.class, MybatisPlusInterceptor.class})
@EnableConfigurationProperties(QuickDevProperties.class)
public class QuickDevAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "quick-dev", name = "enabled", havingValue = "true", matchIfMissing = true)
    public QuickCrudRegistrar quickCrudRegistrar(QuickDevProperties properties) {
        return new QuickCrudRegistrar(properties.getCrud().getDefaultIncludes(),
                properties.getCrud().getDefaultExcludes());
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
    public OperationLogSink quickDevSlf4jOperationLogSink() {
        return new Slf4jOperationLogSink();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
    @ConditionalOnProperty(prefix = "quick-dev.log", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public QuickLogAspect quickLogAspect(OperationLogSink sink, ApplicationContext applicationContext) {
        ObjectMapper objectMapper = applicationContext.getBeanProvider(ObjectMapper.class)
                .getIfAvailable(ObjectMapper::new);
        // 默认 sink 仅打印（Slf4j）；写库等落地方式由用户自实现 OperationLogSink
        // 并自行决定线程模型（同步/异步批量）
        return new QuickLogAspect(sink, objectMapper);
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
        // 静态注册由 quickDevStaticConfigurer 统一完成（支持用户自定义 Executor Bean 覆盖）
        return new TranslateExecutor(
                translate.isEnabled(), translate.getCacheSeconds() * 1000);
    }

    /**
     * @Translate(mode = APPEND) 附加模式：注册 Module Bean，Spring Boot 自动装配进
     * ObjectMapper，序列化期为 APPEND 字段追加兄弟属性输出翻译结果
     */
    @Bean
    @ConditionalOnClass(ObjectMapper.class)
    @ConditionalOnProperty(prefix = "quick-dev.translate", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public TranslateAppendModule translateAppendModule() {
        return new TranslateAppendModule();
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

    /**
     * 静态装配：把 quick-dev.limits.* 写入 core 的静态上限、装配校验开关，
     * 并把（用户自定义或框架默认的）TranslateExecutor 登记到静态入口。
     *
     * <p>用类型化的 {@link SmartInitializingSingleton} 而不是返回 String 的 Bean：
     * 后者会污染容器的 by-type 查找，且没有稳定的初始化时机——
     * 这里保证在所有单例就绪后执行，读到的都是最终配置。</p>
     */
    @Bean
    public QuickDevStaticConfigurer quickDevStaticConfigurer(QuickDevProperties properties,
                                                              ObjectProvider<TranslateExecutor> executorProvider) {
        return new QuickDevStaticConfigurer(properties, executorProvider);
    }
}
