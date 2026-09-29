package dev.xuya.core.translate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 内置数据库字典：字典存数据库表（默认约定 sys_dict(dict_type, dict_value, dict_label)），
 * 一个类同时提供正向（值-&gt;标签）与反向（标签-&gt;值，Excel 导入）解析，零代码接入：
 *
 * <pre>
 * quick-dev:
 *   dict:
 *     enabled: true       # classpath 有 JdbcTemplate 时自动生效
 *     table: sys_dict
 *     type-column: dict_type
 *     value-column: dict_value
 *     label-column: dict_label
 * </pre>
 *
 * <p>已自定义 DictResolver / DictReverseResolver 时自动让位（条件装配）。
 * 查询结果由 TranslateExecutor 的 TTL 缓存兜底，本类不做缓存。</p>
 */
public class JdbcDictProvider implements DictResolver, DictReverseResolver {

    private static final Logger log = LoggerFactory.getLogger(JdbcDictProvider.class);
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_]+");

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final String byValueSql;
    private final String byLabelSql;

    public JdbcDictProvider(ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                            String table, String typeColumn, String valueColumn, String labelColumn) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.byValueSql = "SELECT " + requireIdentifier(labelColumn)
                + " FROM " + requireIdentifier(table)
                + " WHERE " + requireIdentifier(typeColumn) + " = ? AND " + requireIdentifier(valueColumn)
                + " = ? LIMIT 1";
        this.byLabelSql = "SELECT " + requireIdentifier(valueColumn)
                + " FROM " + requireIdentifier(table)
                + " WHERE " + requireIdentifier(typeColumn) + " = ? AND " + requireIdentifier(labelColumn)
                + " = ? LIMIT 1";
    }

    /** 值 -> 标签（值统一字符串化，与 varchar 字典列匹配，兼容数字/字符串字典值） */
    @Override
    public String resolve(String dictType, Object dictValue) {
        return query(byValueSql, dictType, String.valueOf(dictValue));
    }

    /** 标签 -> 值 */
    @Override
    public Object reverse(String dictType, String label) {
        return query(byLabelSql, dictType, label);
    }

    private String query(String sql, Object... args) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            log.debug("容器中无 JdbcTemplate，内置数据库字典不可用");
            return null;
        }
        List<String> result = jdbcTemplate.queryForList(sql, String.class, args);
        return result.isEmpty() ? null : result.get(0);
    }

    /** 表名/列名只能含字母数字下划线（配置来自开发者，防御性校验） */
    private static String requireIdentifier(String name) {
        if (name == null || !IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("字典表/列名不合法（仅允许字母数字下划线）: " + name);
        }
        return name;
    }
}
