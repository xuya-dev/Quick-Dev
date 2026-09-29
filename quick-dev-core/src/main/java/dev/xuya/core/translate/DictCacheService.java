package dev.xuya.core.translate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 字典内存缓存：全量加载字典表构建双向索引，翻译/反解全部走内存，不查库。
 *
 * <ul>
 *   <li>懒加载：首次访问时自动全量加载一次</li>
 *   <li>刷新：调 {@link #refresh()} 全量重建（框架内置刷新接口，字典变更后调用）</li>
 *   <li>定时刷新：{@code quick-dev.dict.refresh-interval-seconds &gt; 0} 时自管理后台定时重建
 *       （多实例部署下的最终一致方案，无需 Redis）</li>
 *   <li>读无锁：volatile 快照整体替换，线程安全</li>
 * </ul>
 */
public class DictCacheService {

    private static final Logger log = LoggerFactory.getLogger(DictCacheService.class);

    private final DictLoader loader;

    private volatile Snapshot snapshot;
    private volatile java.util.concurrent.ScheduledExecutorService autoRefreshScheduler;

    /**
     * 数据来源为用户自定义 {@link DictLoader}（远程服务/配置中心/自有表）；null 表示仅靠导入端点上传数据
     */
    public DictCacheService(DictLoader loader) {
        this.loader = loader;
    }

    /**
     * 启用定时自动刷新（秒；非正数不启用）。自管理守护线程，不依赖 @EnableScheduling
     */
    public void startAutoRefresh(long intervalSeconds) {
        if (intervalSeconds <= 0 || autoRefreshScheduler != null) {
            return;
        }
        synchronized (this) {
            if (autoRefreshScheduler != null) {
                return;
            }
            autoRefreshScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "quick-dev-dict-refresh");
                thread.setDaemon(true);
                return thread;
            });
            autoRefreshScheduler.scheduleWithFixedDelay(this::refreshQuietly,
                    intervalSeconds, intervalSeconds, java.util.concurrent.TimeUnit.SECONDS);
            log.info("字典定时刷新已启用，间隔 {} 秒", intervalSeconds);
        }
    }

    /**
     * 定时任务入口：吞掉一切异常，否则 ScheduledExecutorService 会取消后续执行
     */
    private void refreshQuietly() {
        try {
            refresh();
        } catch (Exception e) {
            log.warn("字典定时刷新失败（下一轮继续）: {}", e.getMessage());
        }
    }

    /**
     * @PreDestroy 等价清理（本类非必然为 Spring Bean，公共方法供装配方调用）
     */
    public void shutdown() {
        java.util.concurrent.ScheduledExecutorService scheduler = this.autoRefreshScheduler;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /**
     * 全量重建缓存（走 DictLoader；未提供 loader 时不动作——数据来源为导入端点上传）
     */
    public synchronized void refresh() {
        if (loader == null) {
            log.debug("未提供 DictLoader，跳过刷新（字典数据来自导入端点）");
            return;
        }
        long start = System.currentTimeMillis();
        replaceAll(loader.loadAll());
        log.info("字典缓存已从 DictLoader 加载，耗时 {}ms", System.currentTimeMillis() - start);
    }

    /**
     * 全量替换缓存数据（导入端点调用；也适用于任何"一次性给全量"的场景）。
     * 规定格式：[{type, value, label}, ...]，全量替换而非增量合并。
     */
    public synchronized void replaceAll(List<DictLoader.DictEntry> entries) {
        Map<String, Map<String, String>> byValue = new HashMap<>();
        Map<String, Map<String, String>> byLabel = new HashMap<>();
        for (DictLoader.DictEntry row : entries) {
            String type = row.type();
            String value = row.value();
            String label = row.label();
            if (type == null || value == null || label == null) {
                continue;
            }
            // type+value 视为唯一键：重复时后者覆盖；type+label 反解有歧义：取先入库的
            byValue.computeIfAbsent(type, k -> new HashMap<>()).put(value, label);
            byLabel.computeIfAbsent(type, k -> new HashMap<>()).putIfAbsent(label, value);
        }
        int validCount = byValue.values().stream().mapToInt(Map::size).sum();
        this.snapshot = new Snapshot(
                Map.copyOf(byValue), Map.copyOf(byLabel), validCount, Instant.now());
        // 同步清空翻译结果缓存：否则 TTL（默认 60s）内仍返回旧标签
        TranslateExecutor executor = TranslateExecutor.getInstance();
        if (executor != null) {
            executor.clearCache();
        }
    }

    /**
     * 值 -> 标签（缓存未初始化时自动懒加载）
     */
    public String getLabel(String type, String value) {
        ensureLoaded();
        Snapshot current = snapshot;
        return current == null ? null : current.byValue.getOrDefault(type, Map.of()).get(value);
    }

    /**
     * 标签 -> 值（缓存未初始化时自动懒加载）
     */
    public String getValue(String type, String label) {
        ensureLoaded();
        Snapshot current = snapshot;
        return current == null ? null : current.byLabel.getOrDefault(type, Map.of()).get(label);
    }

    public boolean isLoaded() {
        return snapshot != null;
    }

    /**
     * 已缓存的字典条数
     */
    public int size() {
        Snapshot current = snapshot;
        return current == null ? 0 : current.size;
    }

    /**
     * 最近一次加载时间
     */
    public Instant loadedAt() {
        Snapshot current = snapshot;
        return current == null ? null : current.loadedAt;
    }

    private void ensureLoaded() {
        if (snapshot == null && loader != null) {
            refresh();
        }
    }

    private record Snapshot(Map<String, Map<String, String>> byValue,
                            Map<String, Map<String, String>> byLabel,
                            int size, Instant loadedAt) {
    }
}
