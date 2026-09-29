package dev.xuya.core.log;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * 异步操作日志装饰器：把真正的 sink（如写库实现）放到后台单线程执行，
 * 业务线程零等待；队列满或 sink 抛异常时仅告警，绝不影响业务请求。
 *
 * <p>由自动配置在 quick-dev.log.async=true 时包装用户的 OperationLogSink。</p>
 */
public class AsyncOperationLogSink implements OperationLogSink {

    private static final Logger log = LoggerFactory.getLogger(AsyncOperationLogSink.class);

    private final OperationLogSink delegate;
    private final ExecutorService executor;

    public AsyncOperationLogSink(OperationLogSink delegate) {
        this.delegate = delegate;
        this.executor = new ThreadPoolExecutor(
                1, 1, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(10_000),
                r -> {
                    Thread thread = new Thread(r, "quick-dev-op-log");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.DiscardPolicy() {
                    @Override
                    public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                        log.warn("操作日志队列已满，本条日志被丢弃（可在 sink 内做批量/异步优化）");
                    }
                });
    }

    /**
     * 供测试注入同步执行器
     */
    AsyncOperationLogSink(OperationLogSink delegate, ExecutorService executor) {
        this.delegate = delegate;
        this.executor = executor;
    }

    /**
     * 便捷构造：wrap 仅在需要时包装（同步场景直接返回原 sink）
     */
    public static OperationLogSink wrap(OperationLogSink delegate, boolean async) {
        if (!async || delegate instanceof AsyncOperationLogSink) {
            return delegate;
        }
        return new AsyncOperationLogSink(delegate);
    }

    @Override
    public void save(LogRecord record) {
        try {
            executor.execute(() -> {
                try {
                    delegate.save(record);
                } catch (Exception e) {
                    log.warn("操作日志落地失败: {}", e.getMessage());
                }
            });
        } catch (RejectedExecutionException ignored) {
            // DiscardPolicy 已告警
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("操作日志线程关闭超时，剩余日志可能丢失");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
