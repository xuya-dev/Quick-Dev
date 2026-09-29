package dev.xuya.core.translate;

import java.util.List;

/**
 * 字典数据来源 SPI：实现本接口并注册为 Spring Bean，即可让字典翻译/反解/缓存
 * 使用**你自己的数据源**（远程字典服务、配置中心、任意表结构……），
 * 内置的数据库加载器（quick-dev.dict.table）会自动让位。
 *
 * <pre>
 * &#64;Component
 * public class RemoteDictLoader implements DictLoader {
 *     &#64;Override
 *     public List&lt;DictEntry&gt; loadAll() {
 *         return remoteDictClient.fetchAll().stream()
 *                 .map(d -> new DictEntry(d.type(), d.value(), d.label()))
 *                 .toList();
 *     }
 * }
 * </pre>
 *
 * <p>加载时机与缓存完全由框架管理：首次访问懒加载、刷新接口/定时任务全量重建，
 * 实现方只需提供"全量列表"。</p>
 */
@FunctionalInterface
public interface DictLoader {

    /**
     * 全量加载字典条目（refresh 与首次懒加载时调用）
     */
    List<DictEntry> loadAll();

    /**
     * 一条字典：类型 + 值 + 标签
     */
    record DictEntry(String type, String value, String label) {
    }
}
