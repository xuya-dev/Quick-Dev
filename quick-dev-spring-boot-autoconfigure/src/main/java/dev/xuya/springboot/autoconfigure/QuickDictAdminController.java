package dev.xuya.springboot.autoconfigure;

import dev.xuya.core.auth.RequiresPerm;
import dev.xuya.core.common.ParamException;
import dev.xuya.core.common.R;
import dev.xuya.core.translate.DictCacheService;
import dev.xuya.core.translate.TranslateExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置字典管理接口：对 quick-dev.dict.table 配置的字典表做分页查询 / 保存（存在即更新）/ 删除，
 * 每次写操作后自动重建字典缓存并清空翻译结果缓存——字典管理界面保存即生效，无需手动刷新。
 *
 * <p>默认路径前缀 /quick-dev/dict（quick-dev.dict.admin-path 可配），需要 dict:manage 权限码。</p>
 * <pre>
 * GET    {path}/page?type=user_status&current=1&size=10   分页
 * POST   {path}      {"type":"...","value":"...","label":"..."}  新增或更新
 * DELETE {path}?type=&value=                               删除
 * </pre>
 */
@RestController
@RequestMapping("${quick-dev.dict.admin-path:/quick-dev/dict}")
public class QuickDictAdminController {

    private final DictCacheService cacheService;
    private final TranslateExecutor translateExecutor;
    private final JdbcTemplate jdbcTemplate;
    private final String table;
    private final String typeColumn;
    private final String valueColumn;
    private final String labelColumn;

    public QuickDictAdminController(DictCacheService cacheService,
                                    TranslateExecutor translateExecutor,
                                    JdbcTemplate jdbcTemplate,
                                    QuickDevProperties properties) {
        this.cacheService = cacheService;
        this.translateExecutor = translateExecutor;
        this.jdbcTemplate = jdbcTemplate;
        QuickDevProperties.Dict dict = properties.getDict();
        this.table = dict.getTable();
        this.typeColumn = dict.getTypeColumn();
        this.valueColumn = dict.getValueColumn();
        this.labelColumn = dict.getLabelColumn();
    }

    @RequiresPerm("dict:manage")
    @GetMapping("/page")
    public R<Object> page(@RequestParam(required = false) String type,
                          @RequestParam(defaultValue = "1") long current,
                          @RequestParam(defaultValue = "10") long size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (type != null && !type.isBlank()) {
            where.append(" AND ").append(typeColumn).append(" = ?");
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + where, Long.class,
                type == null || type.isBlank() ? new Object[0] : new Object[]{type});
        long limit = Math.min(Math.max(size, 1), 500);
        long offset = Math.max(current - 1, 0) * limit;
        String sql = "SELECT " + typeColumn + ", " + valueColumn + ", " + labelColumn
                + " FROM " + table + where
                + " ORDER BY " + typeColumn + ", " + valueColumn
                + " LIMIT " + limit + " OFFSET " + offset;
        List<Map<String, Object>> records = type == null || type.isBlank()
                ? jdbcTemplate.queryForList(sql)
                : jdbcTemplate.queryForList(sql, type);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("total", total);
        data.put("size", limit);
        data.put("current", current);
        return R.ok(data);
    }

    @RequiresPerm("dict:manage")
    @PostMapping
    public R<Object> save(@RequestBody Map<String, String> body) {
        String type = body.get("type");
        String value = body.get("value");
        String label = body.get("label");
        if (type == null || type.isBlank() || value == null || value.isBlank()
                || label == null || label.isBlank()) {
            throw new ParamException("type、value、label 均不能为空");
        }
        int updated = jdbcTemplate.update("UPDATE " + table + " SET " + labelColumn
                + " = ? WHERE " + typeColumn + " = ? AND " + valueColumn + " = ?",
                label, type, value);
        if (updated == 0) {
            jdbcTemplate.update("INSERT INTO " + table + " (" + typeColumn + ", "
                            + valueColumn + ", " + labelColumn + ") VALUES (?, ?, ?)",
                    type, value, label);
        }
        refreshCache();
        return R.ok("保存成功", Map.of("type", type, "value", value, "label", label));
    }

    @RequiresPerm("dict:manage")
    @DeleteMapping
    public R<Object> delete(@RequestParam String type, @RequestParam String value) {
        int rows = jdbcTemplate.update("DELETE FROM " + table
                        + " WHERE " + typeColumn + " = ? AND " + valueColumn + " = ?",
                type, value);
        refreshCache();
        return R.ok("删除成功", rows);
    }

    private void refreshCache() {
        cacheService.refresh();
        translateExecutor.clearCache();
    }
}
