package dev.xuya.springboot.autoconfigure;

import dev.xuya.core.translate.DictCacheForwardResolver;
import dev.xuya.core.translate.DictCacheReverseResolver;
import dev.xuya.core.translate.DictCacheService;
import dev.xuya.core.translate.DictLoader;
import dev.xuya.core.translate.DictResolver;
import dev.xuya.core.translate.DictReverseResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 字典装配条件：
 * - 各方向独立让位（此前正/反两个方向共用一个 AND 条件，自定义反解会连正向翻译一起丢掉）
 * - 非 servlet 环境整体回退（此前会 NoSuchBeanDefinitionException 崩溃）
 */
class QuickDevDictConfigurationConditionsTest {

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withBean(RequestMappingHandlerMapping.class)
            .withConfiguration(AutoConfigurations.of(
                    QuickDevAutoConfiguration.class, QuickDevDictConfiguration.class));

    @Test
    void defaultContextShouldRegisterBothDirections() {
        webRunner.run(context -> {
            assertThat(context).hasSingleBean(DictCacheService.class);
            assertThat(context).hasSingleBean(DictResolver.class);
            assertThat(context).hasSingleBean(DictReverseResolver.class);
            assertThat(context).hasSingleBean(QuickDictRefreshController.class);
        });
    }

    @Test
    void dictDisabledShouldSkipEverything() {
        webRunner.withPropertyValues("quick-dev.dict.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(DictCacheService.class);
            assertThat(context).doesNotHaveBean(DictResolver.class);
            assertThat(context).doesNotHaveBean(DictReverseResolver.class);
            assertThat(context).doesNotHaveBean(QuickDictRefreshController.class);
        });
    }

    @Test
    void refreshEndpointDisabledShouldOnlySkipController() {
        webRunner.withPropertyValues("quick-dev.dict.refresh-endpoint-enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(QuickDictRefreshController.class);
            assertThat(context).hasSingleBean(DictCacheService.class);
        });
    }

    @Test
    void customReverseResolverShouldKeepBuiltInForwardResolver() {
        // 让位按方向独立：用户只自定义了 DictReverseResolver 时，正向翻译仍由内置兜底
        webRunner.withBean("myReverse", DictReverseResolver.class,
                () -> (dictType, label) -> null).run(context -> {
            DictResolver forward = context.getBean(DictResolver.class);
            assertThat(forward).isInstanceOf(DictCacheForwardResolver.class);

            DictReverseResolver reverse = context.getBean(DictReverseResolver.class);
            assertThat(reverse).isNotInstanceOf(DictCacheReverseResolver.class);
        });
    }

    @Test
    void customForwardResolverShouldKeepBuiltInReverseResolver() {
        webRunner.withBean("myForward", DictResolver.class,
                () -> (dictType, value) -> null).run(context -> {
            DictResolver forward = context.getBean(DictResolver.class);
            assertThat(forward).isNotInstanceOf(DictCacheForwardResolver.class);

            DictReverseResolver reverse = context.getBean(DictReverseResolver.class);
            assertThat(reverse).isInstanceOf(DictCacheReverseResolver.class);
        });
    }

    @Test
    void nonServletEnvironmentShouldSkipDictConfigurationInsteadOfFailingStartup() {
        // 此前 QuickDevDictConfiguration 依赖 web 门控的属性 Bean，非 servlet 应用启动即崩
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(QuickDevDictConfiguration.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DictCacheService.class);
                    assertThat(context).doesNotHaveBean(DictResolver.class);
                });
    }

    @Test
    void loaderBeanShouldBeWiredIntoCacheService() {
        webRunner.withBean("loader", DictLoader.class, () -> List::of)
                .run(context -> assertThat(context).hasSingleBean(DictCacheService.class));
    }
}
