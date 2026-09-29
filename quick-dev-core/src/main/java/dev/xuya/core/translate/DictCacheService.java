package dev.xuya.core.translate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

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
 *   <li>读无锁：volatile 快照整体替换，线程安全</li>
 * </ul>
 */
public class DictCacheService {

    private static final Logger log = LoggerFactory.getLogger(DictCacheService.class);

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final String loadSql;

    private volatile Snapshot snapshot;

    public DictCacheService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                            String table, String typeColumn, String valueColumn, String labelColumn) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.loadSql = "SELECT " + identifier(typeColumn) + ", " + identifier(valueColumn)
                + ", " + identifier(labelColumn) + " FROM " + identifier(table);
    }

    /** 全量重建缓存（幂等，加锁防并发重刷） */
    public synchronized void refresh() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            log.warn("容器中无 JdbcTemplate，字典缓存刷新跳过");
            return;
        }
        long start = System.currentTimeMillis();
        List<String[]> rows = jdbcTemplate.query(loadSql, (rs, i) -> new String[]{
                rs.getString(1), rs.getString(2), rs.getString(3)});
        Map<String, Map<String, String>> byValue = new HashMap<>();
        Map<String, Map<String, String>> byLabel = new HashMap<>();
        for (String[] row : rows) {
            String type = row[0];
            String value = row[1];
            String label = row[2];
            if (type == null || value == null || label == null) {
                continue;
            }
            // type+value 视为唯一键：重复时后者覆盖；type+label 反解有歧义：取先入库的
            byValue.computeIfAbsent(type, k -> new HashMap<>()).put(value, label);
            byLabel.computeIfAbsent(type, k -> new HashMap<>()).putIfAbsent(label, value);
        }
        this.snapshot = new Snapshot(
                Map.copyOf(byValue), Map.copyOf(byLabel), rows.size(), Instant.now());
        log.info("字典缓存已加载 {} 条 / {} 个类型，耗时 {}ms",
                rows.size(), byValue.size(), System.currentTimeMillis() - start);
    }

    /** 值 -> 标签（缓存未初始化时自动懒加载） */
    public String getLabel(String type, String value) {
        ensureLoaded();
        Snapshot current = snapshot;
        return current == null ? null : current.byValue.getOrDefault(type, Map.of()).get(value);
    }

    /** 标签 -> 值（缓存未初始化时自动懒加载） */
    public String getValue(String type, String label) {
        ensureLoaded();
        Snapshot current = snapshot;
        return current == null ? null : current.byLabel.getOrDefault(type, Map.of()).get(label);
    }

    public boolean isLoaded() {
        return snapshot != null;
    }

    /** 已缓存的字典条数 */
    public int size() {
        Snapshot current = snapshot;
        return current == null ? 0 : current.size;
    }

    /** 最近一次加载时间 */
    public Instant loadedAt() {
        Snapshot current = snapshot;
        return current == null ? null : current.loadedAt;
    }

    private void ensureLoaded() {
        if (snapshot == null) {
            refresh();
        }
    }

    private static String identifier(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("字典表/列名不合法（仅允许字母数字下划线）: " + name);
        }
        return name;
    }

    private record Snapshot(Map<String, Map<String, String>> byValue,
                            Map<String, Map<String, String>> byLabel,
                            int size, Instant loadedAt) {
    }
}
