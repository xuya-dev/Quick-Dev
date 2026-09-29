package dev.xuya.demo.log;

import dev.xuya.core.log.LogRecord;
import dev.xuya.core.log.OperationLogSink;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 演示用内存操作日志收集器（同时便于测试断言）。
 * <p>生产环境请替换为实现写库/ES 的 Sink（异步），框架默认提供 Slf4j 输出。</p>
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
