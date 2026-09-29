package dev.xuya.springboot.autoconfigure;

import dev.xuya.core.auth.RequiresPerm;
import dev.xuya.core.common.R;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置操作日志查询端点（审计闭环：记录了还能查）：分页查询 quick-dev.log.table 配置的日志表。
 * <p>默认 GET /quick-dev/log/page（quick-dev.log.query-path 可配），需 log:manage 权限码；
 * 仅在 quick-dev.log.jdbc=true（日志落库）时注册。</p>
 * <p>筛选参数：module / operator / uri / success（1/0），按 id 倒序。</p>
 */
@RestController
@RequestMapping("${quick-dev.log.query-path:/quick-dev/log}")
public class QuickLogQueryController {

    private final JdbcTemplate jdbcTemplate;
    private final String table;

    public QuickLogQueryController(JdbcTemplate jdbcTemplate, QuickDevProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.table = properties.getLog().getTable();
    }

    @RequiresPerm("log:manage")
    @GetMapping("/page")
    public R<Object> page(@RequestParam(required = false) String module,
                          @RequestParam(required = false) String operator,
                          @RequestParam(required = false) String uri,
                          @RequestParam(required = false) Integer success,
                          @RequestParam(defaultValue = "1") long current,
                          @RequestParam(defaultValue = "10") long size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        Object[] args = new Object[5];
        int argc = 0;
        if (notBlank(module)) {
            where.append(" AND module = ?");
            args[argc++] = module;
        }
        if (notBlank(operator)) {
            where.append(" AND operator = ?");
            args[argc++] = operator;
        }
        if (notBlank(uri)) {
            where.append(" AND uri = ?");
            args[argc++] = uri;
        }
        if (success != null) {
            where.append(" AND success = ?");
            args[argc++] = success;
        }

        Object[] countArgs = java.util.Arrays.copyOf(args, argc);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + where, Long.class, countArgs);

        long limit = Math.min(Math.max(size, 1), 500);
        long offset = Math.max(current - 1, 0) * limit;
        String sql = "SELECT * FROM " + table + where + " ORDER BY id DESC LIMIT " + limit + " OFFSET " + offset;
        Object[] pageArgs = java.util.Arrays.copyOf(args, argc);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(sql, pageArgs);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("total", total);
        data.put("size", limit);
        data.put("current", current);
        return R.ok(data);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
