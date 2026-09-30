package dev.xuya.springboot.redis;

import dev.xuya.core.web.RepeatSubmitStore;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Redis 版防重复提交存储：setIfAbsent + 过期时间的原子占位，集群多实例共享。
 */
public class RedisRepeatSubmitStore implements RepeatSubmitStore {

    private final StringRedisTemplate redisTemplate;

    public RedisRepeatSubmitStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryAcquire(String key, long intervalMillis) {
        // Redis 对 0 过期时间报错（invalid expire time），-1 表示永不过期（会把端点永久锁死）。
        // 与内存实现对齐：亚毫秒间隔一律视为 1ms，保证"窗口极小"语义而非"永久/报错"。
        long ttl = Math.max(1, intervalMillis);
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                key, String.valueOf(System.currentTimeMillis()), Duration.ofMillis(ttl));
        return Boolean.TRUE.equals(acquired);
    }
}
