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
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                key, String.valueOf(System.currentTimeMillis()), Duration.ofMillis(intervalMillis));
        return Boolean.TRUE.equals(acquired);
    }
}
