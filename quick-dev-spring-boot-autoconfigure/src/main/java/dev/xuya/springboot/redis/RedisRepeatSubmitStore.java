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
        // 契约：非正窗口 = 不设防（与内存实现一致）。
        // 同时规避 Redis 的两个坑：0 过期时间报错（invalid expire time），
        // -1 过期时间表示永不过期（会把端点永久锁死）。
        if (intervalMillis <= 0) {
            return true;
        }
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                key, String.valueOf(System.currentTimeMillis()), Duration.ofMillis(intervalMillis));
        return Boolean.TRUE.equals(acquired);
    }
}
