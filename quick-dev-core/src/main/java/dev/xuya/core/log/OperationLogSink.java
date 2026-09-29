package dev.xuya.core.log;

/**
 * 操作日志落地 SPI：实现并注册为 Spring Bean 即接管 @QuickLog 产生的审计记录
 * （写库、ES、消息队列均可；生产建议异步写入）。
 * <p>未提供实现时框架默认输出到 Slf4j（logger: quick-dev.operation-log）。</p>
 */
@FunctionalInterface
public interface OperationLogSink {

    void save(LogRecord record);
}
