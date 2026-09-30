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
 *
 * <p>注意：classpath 有 spring-data-redis 但容器没有 {@link StringRedisTemplate} Bean
 * （例如排除了 RedisAutoConfiguration）时<b>不算错误</b>——返回 null 让内存实现兜底，
 * 而不是让整个应用启动失败。</p>
 */
@AutoConfiguration(before = QuickDevAutoConfiguration.class)
@ConditionalOnClass(StringRedisTemplate.class)
public class QuickDevRedisConfiguration {

    @Bean
    @ConditionalOnMissingBean(RepeatSubmitStore.class)
    @ConditionalOnProperty(prefix = "quick-dev.repeat-submit", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public RepeatSubmitStore redisRepeatSubmitStore(ObjectProvider<StringRedisTemplate> templateProvider) {
        // getIfAvailable（而非 getObject）：没有 Redis 连接配置时静默让位给内存实现
        StringRedisTemplate template = templateProvider.getIfAvailable();
        return template == null ? null : new RedisRepeatSubmitStore(template);
    }
}
