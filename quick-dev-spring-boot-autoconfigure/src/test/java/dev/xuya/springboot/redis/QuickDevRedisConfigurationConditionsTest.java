package dev.xuya.springboot.redis;

import dev.xuya.core.web.MemoryRepeatSubmitStore;
import dev.xuya.core.web.RepeatSubmitInterceptor;
import dev.xuya.core.web.RepeatSubmitStore;
import dev.xuya.springboot.autoconfigure.QuickDevAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 防重装配条件：有 StringRedisTemplate Bean 用 Redis；没有则回落内存，
 * 且绝不因"classpath 有 spring-data-redis 而容器无连接"导致启动失败（此前的 getObject 崩溃路径）。
 */
class QuickDevRedisConfigurationConditionsTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withBean(RequestMappingHandlerMapping.class)
            .withConfiguration(AutoConfigurations.of(
                    QuickDevRedisConfiguration.class, QuickDevAutoConfiguration.class));

    @Test
    void withRedisTemplateShouldBindRedisStoreToInterceptor() {
        runner.withBean("redisTemplate", StringRedisTemplate.class,
                        () -> Mockito.mock(StringRedisTemplate.class, Mockito.RETURNS_MOCKS))
                .run(context -> {
                    assertThat(context).hasSingleBean(RepeatSubmitStore.class);
                    assertThat(context.getBean(RepeatSubmitStore.class)).isInstanceOf(RedisRepeatSubmitStore.class);

                    // 排序回归：拦截器拿到的是 Redis 实现，而不是内存兜底
                    RepeatSubmitInterceptor interceptor = context.getBean(RepeatSubmitInterceptor.class);
                    Object store = ReflectionTestUtils.getField(interceptor, "store");
                    assertThat(store).isInstanceOf(RedisRepeatSubmitStore.class);
                });
    }

    @Test
    void withoutRedisTemplateShouldFallBackToMemoryInsteadOfFailingStartup() {
        // classpath 有 spring-data-redis（本模块 test classpath 即有），
        // 但容器没有 StringRedisTemplate：不应 NoSuchBeanDefinitionException 崩溃
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(RedisRepeatSubmitStore.class);

            RepeatSubmitInterceptor interceptor = context.getBean(RepeatSubmitInterceptor.class);
            Object store = ReflectionTestUtils.getField(interceptor, "store");
            assertThat(store).isInstanceOf(MemoryRepeatSubmitStore.class);
        });
    }

    @Test
    void repeatSubmitDisabledShouldSkipStoreEvenWithRedis() {
        runner.withBean("redisTemplate", StringRedisTemplate.class,
                        () -> Mockito.mock(StringRedisTemplate.class))
                .withPropertyValues("quick-dev.repeat-submit.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(RepeatSubmitStore.class));
    }

    @Test
    void userStoreShouldAlwaysWinOverRedis() {
        runner.withBean("redisTemplate", StringRedisTemplate.class,
                        () -> Mockito.mock(StringRedisTemplate.class))
                .withBean("myStore", RepeatSubmitStore.class, () -> (key, interval) -> true)
                .run(context -> {
                    assertThat(context.getBean(RepeatSubmitStore.class))
                            .isNotInstanceOf(RedisRepeatSubmitStore.class);
                });
    }
}
