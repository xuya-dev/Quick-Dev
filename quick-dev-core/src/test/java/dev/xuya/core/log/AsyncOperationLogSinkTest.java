package dev.xuya.core.log;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AsyncOperationLogSinkTest {

    @Test
    void shouldDelegateToUnderlyingSink() throws Exception {
        List<LogRecord> saved = new CopyOnWriteArrayList<>();
        ExecutorService direct = Executors.newSingleThreadExecutor();
        try {
            OperationLogSink async = new AsyncOperationLogSink(saved::add, direct);
            async.save(new LogRecord());
            direct.shutdown();
            // 同一执行器串行执行，shutdown 前任务已入队
            assertThat(direct.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(saved).hasSize(1);
        } finally {
            direct.shutdownNow();
        }
    }

    @Test
    void sinkExceptionShouldNeverPropagate() throws Exception {
        ExecutorService direct = Executors.newSingleThreadExecutor();
        try {
            OperationLogSink async = new AsyncOperationLogSink(record -> {
                throw new IllegalStateException("db down");
            }, direct);
            LogRecord record = new LogRecord();
            assertThatCode(() -> async.save(record)).doesNotThrowAnyException();
            direct.shutdown();
            assertThat(direct.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        } finally {
            direct.shutdownNow();
        }
    }

    @Test
    void wrapShouldSkipWhenAsyncDisabledOrAlreadyWrapped() {
        OperationLogSink plain = record -> {
        };
        assertThat(AsyncOperationLogSink.wrap(plain, false)).isSameAs(plain);
        AsyncOperationLogSink wrapped = new AsyncOperationLogSink(record -> {
        });
        assertThat(AsyncOperationLogSink.wrap(wrapped, true)).isSameAs(wrapped);
        assertThat(AsyncOperationLogSink.wrap(plain, true)).isInstanceOf(AsyncOperationLogSink.class);
    }
}
