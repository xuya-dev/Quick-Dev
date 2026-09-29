package dev.xuya.demo.log;

import dev.xuya.core.log.LogRecord;
import dev.xuya.core.log.OperationLogSink;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 演示方自定义操作日志落地：内存收集（便于测试断言）。
 * <p>框架只提供 OperationLogSink SPI 与 Slf4j 兜底输出，不直接写库——
 * 生产环境实现写库/ES 的 Sink（建议配合 quick-dev.log.async 异步）。</p>
 */
@Component
public class MemoryLogSink implements OperationLogSink {

    private final ConcurrentLinkedQueue<LogRecord> records = new ConcurrentLinkedQueue<>();

    @Override
    public void save(LogRecord record) {
        records.add(record);
        if (records.size() > 100) {
            records.poll();
        }
    }

    public LogRecord lastRecord() {
        return records.peek();
    }
}
