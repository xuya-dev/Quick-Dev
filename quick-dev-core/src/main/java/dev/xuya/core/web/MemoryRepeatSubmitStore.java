package dev.xuya.core.web;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内防重复提交存储（默认实现）：时间戳指纹 + 惰性清理。
 * 单实例部署足够；集群请使用 Redis 实现。
 */
public class MemoryRepeatSubmitStore implements RepeatSubmitStore {

    private static final int CLEAN_THRESHOLD = 10_000;

    private final Map<String, Long> lastSubmit = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, long intervalMillis) {
        long now = System.currentTimeMillis();
        // compute 原子执行；被拒绝时保留原时间戳——重试不续期窗口，连续点击不会无限推迟放行
        boolean[] allowed = {false};
        lastSubmit.compute(key, (k, previous) -> {
            if (previous != null && now - previous < intervalMillis) {
                return previous;
            }
            allowed[0] = true;
            return now;
        });
        if (lastSubmit.size() > CLEAN_THRESHOLD) {
            clean(now, intervalMillis);
        }
        return allowed[0];
    }

    /**
     * 惰性清理过期指纹，避免长期运行内存增长
     */
    private void clean(long now, long maxInterval) {
        Iterator<Map.Entry<String, Long>> iterator = lastSubmit.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (now - entry.getValue() > maxInterval) {
                iterator.remove();
            }
        }
    }
}
