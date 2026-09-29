package dev.xuya.core.translate;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 内置字典数据来源：全量加载数据库表（默认约定 sys_dict(dict_type, dict_value, dict_label)，
 * 表/列 quick-dev.dict.* 可配）。用户注册自定义 {@link DictLoader} 后本实现自动让位。
 */
public class JdbcDictLoader implements DictLoader {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final String loadSql;

    public JdbcDictLoader(ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                          String table, String typeColumn, String valueColumn, String labelColumn) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.loadSql = "SELECT " + identifier(typeColumn) + ", " + identifier(valueColumn)
                + ", " + identifier(labelColumn) + " FROM " + identifier(table);
    }

    @Override
    public List<DictEntry> loadAll() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            return List.of();
        }
        List<DictEntry> entries = new ArrayList<>();
        jdbcTemplate.query(loadSql, rs -> {
            entries.add(new DictEntry(rs.getString(1), rs.getString(2), rs.getString(3)));
        });
        return entries;
    }

    private static String identifier(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("字典表/列名不合法（仅允许字母数字下划线）: " + name);
        }
        return name;
    }
}
