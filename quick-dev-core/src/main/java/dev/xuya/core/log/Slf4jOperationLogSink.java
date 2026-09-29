package dev.xuya.core.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认日志落地：输出到 Slf4j（logger: quick-dev.operation-log）。
 * 生产环境建议实现 OperationLogSink 异步写库并覆盖本 Bean。
 */
public class Slf4jOperationLogSink implements OperationLogSink {

    private static final Logger log = LoggerFactory.getLogger("quick-dev.operation-log");

    @Override
    public void save(LogRecord r) {
        log.info("[{}] {} | operator={} | {} {} | cost={}ms | success={} | code={} | params={} | error={}",
                r.getModule(), r.getDescription(), r.getOperator(), r.getHttpMethod(), r.getUri(),
                r.getCostMs(), r.isSuccess(), r.getResultCode(), r.getParams(), r.getErrorMessage());
    }
}
