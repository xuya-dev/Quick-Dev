package dev.xuya.core.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 进程内防重存储：窗口内拒绝、不续期窗口、条目按自身过期时间独立淘汰
 */
class MemoryRepeatSubmitStoreTest {

    private final MemoryRepeatSubmitStore store = new MemoryRepeatSubmitStore();

    @Test
    void shouldAllowFirstAndRejectSecondWithinWindow() throws InterruptedException {
        assertThat(store.tryAcquire("k", 200)).isTrue();
        assertThat(store.tryAcquire("k", 200)).isFalse();

        Thread.sleep(250);
        assertThat(store.tryAcquire("k", 200)).isTrue();
    }

    @Test
    void rejectedAttemptShouldNotExtendTheWindow() throws InterruptedException {
        // 被拒时不续期：第 120ms 被拒后，窗口仍从首次成功起算，
        // 第 240ms（> 200ms）即放行——修复前"重试会无限推迟放行"
        assertThat(store.tryAcquire("k", 200)).isTrue();     // t=0
        Thread.sleep(120);
        assertThat(store.tryAcquire("k", 200)).isFalse();    // t≈120，窗口内
        Thread.sleep(130);
        assertThat(store.tryAcquire("k", 200)).isTrue();     // t≈250，窗口已过
    }

    @Test
    void differentKeysShouldHaveIndependentWindows() throws InterruptedException {
        // 条目按自身过期时间淘汰：A 的小窗口过期不影响 B 的大窗口
        assertThat(store.tryAcquire("a", 50)).isTrue();
        assertThat(store.tryAcquire("b", 10_000)).isTrue();

        Thread.sleep(90);
        assertThat(store.tryAcquire("a", 50)).isTrue();      // a 已过期
        assertThat(store.tryAcquire("b", 10_000)).isFalse(); // b 仍在窗口内
    }

    @Test
    void nonPositiveIntervalShouldDisableTheGuard() {
        // 契约：interval <= 0 = 不设防（与 Redis 实现一致）
        assertThat(store.tryAcquire("k", 0)).isTrue();
        assertThat(store.tryAcquire("k", 0)).isTrue();
        assertThat(store.tryAcquire("k", -1)).isTrue();
    }
}
