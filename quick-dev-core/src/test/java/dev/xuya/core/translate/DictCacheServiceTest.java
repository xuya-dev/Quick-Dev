package dev.xuya.core.translate;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DictCacheServiceTest {

    @Test
    void shouldLazilyLoadAndResolveBothDirections() {
        MutableLoader loader = new MutableLoader();
        loader.entries.add(new DictLoader.DictEntry("user_status", "1", "启用"));
        loader.entries.add(new DictLoader.DictEntry("user_status", "0", "停用"));
        DictCacheService cache = new DictCacheService(loader);

        assertThat(cache.isLoaded()).isFalse();
        // 首次访问懒加载
        assertThat(cache.getLabel("user_status", "1")).isEqualTo("启用");
        assertThat(cache.getLabel("user_status", "0")).isEqualTo("停用");
        assertThat(cache.getValue("user_status", "停用")).isEqualTo("0");
        assertThat(cache.getLabel("user_status", "9")).isNull();   // 未命中
        assertThat(cache.getLabel("not_exist", "1")).isNull();     // 未知类型
        assertThat(cache.isLoaded()).isTrue();
        assertThat(cache.size()).isEqualTo(2);
        assertThat(loader.loadCount.get()).isEqualTo(1);          // 只加载一次
    }

    @Test
    void refreshShouldReloadFromLoaderAndApplyChanges() {
        MutableLoader loader = new MutableLoader();
        loader.entries.add(new DictLoader.DictEntry("t", "1", "旧标签"));
        DictCacheService cache = new DictCacheService(loader);
        assertThat(cache.getLabel("t", "1")).isEqualTo("旧标签");

        // 数据源变更 -> refresh 生效
        loader.entries.clear();
        loader.entries.add(new DictLoader.DictEntry("t", "1", "新标签"));
        cache.refresh();
        assertThat(cache.getLabel("t", "1")).isEqualTo("新标签");
        assertThat(cache.getValue("t", "新标签")).isEqualTo("1");
    }

    @Test
    void autoRefreshShouldReloadPeriodically() throws Exception {
        MutableLoader loader = new MutableLoader();
        loader.entries.add(new DictLoader.DictEntry("t", "1", "v1"));
        DictCacheService cache = new DictCacheService(loader);
        cache.startAutoRefresh(1); // 1 秒
        try {
            assertThat(cache.getLabel("t", "1")).isEqualTo("v1");
            loader.entries.set(0, new DictLoader.DictEntry("t", "1", "v2"));
            long deadline = System.currentTimeMillis() + 5000;
            String label = "v1";
            while (System.currentTimeMillis() < deadline) {
                label = cache.getLabel("t", "1");
                if ("v2".equals(label)) {
                    break;
                }
                Thread.sleep(200);
            }
            assertThat(label).as("定时刷新应加载新数据").isEqualTo("v2");
        } finally {
            cache.shutdown();
        }
    }

    @Test
    void malformedEntriesShouldBeSkipped() {
        MutableLoader loader = new MutableLoader();
        loader.entries.add(new DictLoader.DictEntry(null, "1", "x"));      // type 缺失
        loader.entries.add(new DictLoader.DictEntry("t", null, "x"));      // value 缺失
        loader.entries.add(new DictLoader.DictEntry("t", "1", null));      // label 缺失
        loader.entries.add(new DictLoader.DictEntry("t", "1", "ok"));
        DictCacheService cache = new DictCacheService(loader);
        assertThat(cache.getLabel("t", "1")).isEqualTo("ok");
        assertThat(cache.size()).isEqualTo(1);
    }

    /**
     * 可变数据的内存 Loader：模拟用户自定义数据源（远程服务/配置中心）
     */
    private static class MutableLoader implements DictLoader {
        final List<DictEntry> entries = new ArrayList<>();
        final AtomicInteger loadCount = new AtomicInteger();

        @Override
        public List<DictEntry> loadAll() {
            loadCount.incrementAndGet();
            return List.copyOf(entries);
        }
    }
}
