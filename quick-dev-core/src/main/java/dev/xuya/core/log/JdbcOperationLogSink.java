package dev.xuya.core.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

/**
 * 内置 JDBC 操作日志落地：把 {@link LogRecord} 写入约定表（默认 log_record），
 * `quick-dev.log.jdbc=true` 即启用，无需自己实现 Sink。
 *
 * <p>建表 DDL（列名固定约定，表名 quick-dev.log.table 可配）：</p>
 * <pre>
 * create table log_record (
 *     id            bigint primary key auto_increment,
 *     module        varchar(64),
 *     description   varchar(200),
 *     operator      varchar(64),
 *     uri           varchar(200),
 *     http_method   varchar(16),
 *     ip            varchar(64),
 *     params        varchar(2000),
 *     result_code   int,
 *     success       tinyint,
 *     error_message varchar(1000),
 *     cost_ms       bigint,
 *     create_time   timestamp
 * );
 * </pre>
 *
 * <p>与 {@code quick-dev.log.async} 可叠加：异步执行本 Sink 的 INSERT。</p>
 */
public class JdbcOperationLogSink implements OperationLogSink {

    private static final Logger log = LoggerFactory.getLogger(JdbcOperationLogSink.class);

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final String insertSql;

    public JdbcOperationLogSink(ObjectProvider<JdbcTemplate> jdbcTemplateProvider, String table) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        if (table == null || !table.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("日志表名不合法（仅允许字母数字下划线）: " + table);
        }
        this.insertSql = "INSERT INTO " + table + " (module, description, operator, uri, http_method, ip,"
                + " params, result_code, success, error_message, cost_ms, create_time)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    @Override
    public void save(LogRecord r) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            log.warn("容器中无 JdbcTemplate，操作日志未落库");
            return;
        }
        jdbcTemplate.update(insertSql,
                r.getModule(), r.getDescription(),
                r.getOperator() == null ? null : String.valueOf(r.getOperator()),
                r.getUri(), r.getHttpMethod(), r.getIp(), r.getParams(),
                r.getResultCode(), r.isSuccess() ? 1 : 0,
                r.getErrorMessage(), r.getCostMs(), LocalDateTime.now());
    }
}
