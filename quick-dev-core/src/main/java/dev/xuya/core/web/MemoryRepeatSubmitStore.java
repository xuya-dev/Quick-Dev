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
        Long previous = lastSubmit.put(key, now);
        if (lastSubmit.size() > CLEAN_THRESHOLD) {
            clean(now, intervalMillis);
        }
        return previous == null || now - previous >= intervalMillis;
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
