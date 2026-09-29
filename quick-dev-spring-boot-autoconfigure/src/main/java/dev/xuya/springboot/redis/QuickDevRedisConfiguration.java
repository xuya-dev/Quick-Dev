package dev.xuya.springboot.redis;

import dev.xuya.core.web.RepeatSubmitStore;
import dev.xuya.springboot.autoconfigure.QuickDevAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 自动装配：classpath 存在 Redis（引入 quick-dev-redis-spring-boot-starter
 * 或自带 spring-boot-starter-data-redis）且未自定义 {@link RepeatSubmitStore} 时，
 * 防重复提交自动切换为 Redis 原子实现。
 *
 * <p>本配置需在 {@link QuickDevAutoConfiguration} 之前处理，确保内存兜底实现让位。</p>
 */
@AutoConfiguration(before = QuickDevAutoConfiguration.class)
@ConditionalOnClass(StringRedisTemplate.class)
public class QuickDevRedisConfiguration {

    @Bean
    @ConditionalOnMissingBean(RepeatSubmitStore.class)
    @ConditionalOnProperty(prefix = "quick-dev.repeat-submit", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public RepeatSubmitStore redisRepeatSubmitStore(ObjectProvider<StringRedisTemplate> templateProvider) {
        return new RedisRepeatSubmitStore(templateProvider.getObject());
    }
}
