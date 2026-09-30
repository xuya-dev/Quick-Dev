package dev.xuya.core.web;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内防重复提交存储（默认实现）：按条目记录<b>过期时间戳</b>，惰性清理。
 * 单实例部署足够；集群请使用 Redis 实现。
 *
 * <p>被拒绝时不续期窗口——重试不会无限推迟放行；清理时按每个条目自身的
 * 过期时间判定，不同接口的不同窗口（如 1s 与 60s）互不干扰。</p>
 */
public class MemoryRepeatSubmitStore implements RepeatSubmitStore {

    private static final int CLEAN_THRESHOLD = 10_000;

    /** key -> 过期时间戳（毫秒） */
    private final Map<String, Long> expiryByKey = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, long intervalMillis) {
        long now = System.currentTimeMillis();
        // compute 原子执行；被拒绝时保留原过期时间——重试不续期窗口，连续点击不会无限推迟放行
        boolean[] allowed = {false};
        expiryByKey.compute(key, (k, previousExpiry) -> {
            if (previousExpiry != null && now < previousExpiry) {
                return previousExpiry;
            }
            allowed[0] = true;
            return now + Math.max(1, intervalMillis);
        });
        if (expiryByKey.size() > CLEAN_THRESHOLD) {
            clean(now);
        }
        return allowed[0];
    }

    /**
     * 惰性清理过期指纹，避免长期运行内存增长。
     * 以条目自身的过期时间为准：清理不能借用"当前这次请求"的窗口，
     * 否则小窗口接口的请求会误删大窗口接口（如 60s）尚未过期的指纹。
     */
    private void clean(long now) {
        Iterator<Map.Entry<String, Long>> iterator = expiryByKey.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (now >= entry.getValue()) {
                iterator.remove();
            }
        }
    }
}
