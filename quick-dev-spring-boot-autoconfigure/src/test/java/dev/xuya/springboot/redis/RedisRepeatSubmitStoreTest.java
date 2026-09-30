package dev.xuya.springboot.redis;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class RedisRepeatSubmitStoreTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
    private final StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
    private final RedisRepeatSubmitStore store = new RedisRepeatSubmitStore(redisTemplate);

    @Test
    void shouldReturnTrueWhenKeyAcquired() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        assertThat(store.tryAcquire("quick-dev:repeat:t:abc:POST:/order", 2000)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenKeyExists() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        assertThat(store.tryAcquire("quick-dev:repeat:t:abc:POST:/order", 2000)).isFalse();
    }

    @Test
    void shouldReturnFalseWhenRedisReturnsNull() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(null);
        assertThat(store.tryAcquire("k", 1000)).isFalse();
    }

    @Test
    void acquireShouldCarryIntervalAsExpiry() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        store.tryAcquire("k", 1500);
        Mockito.verify(valueOperations).setIfAbsent(eq("k"), anyString(), eq(Duration.ofMillis(1500)));
    }

    @Test
    void nonPositiveIntervalShouldDisableGuardWithoutTouchingRedis() {
        // 契约：interval <= 0 = 不设防（与内存实现一致），且不触发任何 Redis 调用
        assertThat(store.tryAcquire("k", 0)).isTrue();
        assertThat(store.tryAcquire("k", -5)).isTrue();
        Mockito.verifyNoInteractions(valueOperations);
    }
}
