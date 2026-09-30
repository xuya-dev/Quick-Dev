package dev.xuya.springboot.autoconfigure;

import dev.xuya.core.auth.AuthInterceptor;
import dev.xuya.core.common.QuickDevLimits;
import dev.xuya.core.common.R;
import dev.xuya.core.crud.AutoFillMetaObjectHandler;
import dev.xuya.core.crud.QuickCrudRegistrar;
import dev.xuya.core.log.OperationLogSink;
import dev.xuya.core.log.QuickLogAspect;
import dev.xuya.core.log.Slf4jOperationLogSink;
import dev.xuya.core.methodop.QuickOpAspect;
import dev.xuya.core.translate.TranslateAppendModule;
import dev.xuya.core.translate.TranslateExecutor;
import dev.xuya.core.web.GlobalExceptionHandler;
import dev.xuya.core.web.RepeatSubmitInterceptor;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自动装配条件矩阵：每个 @ConditionalOnProperty/@ConditionalOnMissingBean 开关的
 * 行为契约（此前这些开关全部零测试覆盖）。
 */
class QuickDevAutoConfigurationConditionsTest {

    /**
     * 裸 runner 没有 WebMvc 自动配置：QuickCrudRegistrar 启动时需要 RequestMappingHandlerMapping，
     * 手动注册一个空实例（无 @QuickCrud Bean 时 registrar 扫描后即返回）
     */
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withBean(RequestMappingHandlerMapping.class)
            .withConfiguration(AutoConfigurations.of(QuickDevAutoConfiguration.class));

    @AfterEach
    void restoreStaticLimits() {
        QuickDevLimits.setQueryMaxRows(1_000);
        QuickDevLimits.setExportMaxRows(100_000);
        QuickDevLimits.setExportBatchSize(1_000);
        QuickDevLimits.setImportMaxRows(10_000);
        QuickDevLimits.setInMaxSize(1_000);
    }

    // ------------------------------------------------------------------
    // 默认装配
    // ------------------------------------------------------------------

    @Test
    void defaultContextShouldRegisterAllFrameworkBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(QuickCrudRegistrar.class);
            assertThat(context).hasSingleBean(AutoFillMetaObjectHandler.class);
            assertThat(context).hasSingleBean(AuthInterceptor.class);
            assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
            assertThat(context).hasSingleBean(TranslateExecutor.class);
            assertThat(context).hasSingleBean(TranslateAppendModule.class);
            assertThat(context).hasSingleBean(QuickOpAspect.class);
            assertThat(context).hasSingleBean(QuickLogAspect.class);
            assertThat(context).hasSingleBean(OperationLogSink.class);
            assertThat(context).hasSingleBean(RepeatSubmitInterceptor.class);
            assertThat(context).hasSingleBean(MybatisPlusInterceptor.class);
            assertThat(context).hasSingleBean(QuickDevStaticConfigurer.class);
        });
    }

    // ------------------------------------------------------------------
    // @ConditionalOnProperty 开关
    // ------------------------------------------------------------------

    @Test
    void enabledFalseShouldSkipCrudRegistrarOnly() {
        runner.withPropertyValues("quick-dev.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(QuickCrudRegistrar.class);
            // 其余设施不受影响
            assertThat(context).hasSingleBean(AuthInterceptor.class);
            assertThat(context).hasSingleBean(TranslateExecutor.class);
        });
    }

    @Test
    void methodOpDisabledShouldSkipQuickOpAspect() {
        runner.withPropertyValues("quick-dev.method-op.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(QuickOpAspect.class));
    }

    @Test
    void autoFillDisabledShouldSkipMetaObjectHandler() {
        runner.withPropertyValues("quick-dev.auto-fill.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(AutoFillMetaObjectHandler.class));
    }

    @Test
    void logDisabledShouldSkipAspectButKeepSink() {
        runner.withPropertyValues("quick-dev.log.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(QuickLogAspect.class);
            // 默认 Sink 独立于开关，始终兜底
            assertThat(context).hasSingleBean(OperationLogSink.class);
        });
    }

    @Test
    void repeatSubmitDisabledShouldSkipInterceptor() {
        runner.withPropertyValues("quick-dev.repeat-submit.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(RepeatSubmitInterceptor.class));
    }

    @Test
    void translateDisabledShouldSkipAppendModuleButKeepExecutorBean() {
        runner.withPropertyValues("quick-dev.translate.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(TranslateAppendModule.class);
            // Executor Bean 仍存在，内部 enabled=false 由静态装配器登记
            assertThat(context).hasSingleBean(TranslateExecutor.class);
        });
    }

    // ------------------------------------------------------------------
    // @ConditionalOnMissingBean 让位
    // ------------------------------------------------------------------

    @Test
    void userMybatisPlusInterceptorShouldTakePrecedence() {
        runner.withBean(MybatisPlusInterceptor.class).run(context -> {
            // 用户自定义后框架不再注册自己的分页兜底（只有一个该类型 Bean）
            MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);
            assertThat(interceptor).isSameAs(context.getBean(MybatisPlusInterceptor.class));
        });
    }

    @Test
    void userOperationLogSinkShouldReplaceSlf4jDefault() {
        runner.withBean("mySink", OperationLogSink.class, () -> record -> {
        }).run(context -> {
            OperationLogSink sink = context.getBean(OperationLogSink.class);
            assertThat(sink).isNotInstanceOf(Slf4jOperationLogSink.class);
            // 切面仍然装配（依赖的是 Sink 接口）
            assertThat(context).hasSingleBean(QuickLogAspect.class);
        });
    }

    // ------------------------------------------------------------------
    // 静态装配器：limits 真正写进 core 静态值（此前返回 String Bean 无任何顺序保证）
    // ------------------------------------------------------------------

    @Test
    void staticConfigurerShouldApplyConfiguredLimits() {
        int originalQuery = QuickDevLimits.getQueryMaxRows();
        try {
            runner.withPropertyValues(
                    "quick-dev.limits.query-max-rows=42",
                    "quick-dev.limits.export-batch-size=7").run(context -> {
                assertThat(QuickDevLimits.getQueryMaxRows()).isEqualTo(42);
                assertThat(QuickDevLimits.getExportBatchSize()).isEqualTo(7);
            });
        } finally {
            QuickDevLimits.setQueryMaxRows(originalQuery);
        }
    }

    // ------------------------------------------------------------------
    // error-detail 语义
    // ------------------------------------------------------------------

    @Test
    void errorDetailFalseShouldMaskUnexpectedExceptionMessage() {
        // 默认 false：只返回通用提示（0.5.0 起安全默认）
        runner.run(context -> {
            GlobalExceptionHandler handler = context.getBean(GlobalExceptionHandler.class);
            R<Void> masked = handler.handleOther(new RuntimeException("内部 jdbc:secret 细节"));
            assertThat(masked.getMsg()).isEqualTo("系统繁忙，请稍后重试");
            assertThat(masked.getMsg()).doesNotContain("jdbc");
        });
        // 显式 true（调试）：透出原始信息
        runner.withPropertyValues("quick-dev.error-detail=true").run(context -> {
            GlobalExceptionHandler handler = context.getBean(GlobalExceptionHandler.class);
            R<Void> detail = handler.handleOther(new RuntimeException("内部 jdbc:secret 细节"));
            assertThat(detail.getMsg()).contains("jdbc:secret");
        });
    }
}
