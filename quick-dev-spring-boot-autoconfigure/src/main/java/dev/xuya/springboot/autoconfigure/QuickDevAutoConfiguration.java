package dev.xuya.springboot.autoconfigure;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import dev.xuya.core.auth.AuthInterceptor;
import dev.xuya.core.auth.AuthSettings;
import dev.xuya.core.crud.AutoFillMetaObjectHandler;
import dev.xuya.core.crud.QuickCrudRegistrar;
import dev.xuya.core.web.GlobalExceptionHandler;
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
    public WebMvcConfigurer quickDevAuthWebMvcConfigurer(AuthInterceptor authInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(authInterceptor).addPathPatterns("/**");
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.web.bind.annotation.RestControllerAdvice")
    public GlobalExceptionHandler quickDevGlobalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
