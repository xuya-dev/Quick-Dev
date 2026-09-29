package dev.xuya.core.web;

/**
 * 防重复提交指纹存储 SPI。
 * <ul>
 *   <li>默认：{@link MemoryRepeatSubmitStore} 进程内实现（零依赖）</li>
 *   <li>引入 Redis 后：框架自动装配 Redis 原子实现（setIfAbsent + 过期），集群共享</li>
 *   <li>自定义：实现本接口并注册 Bean 覆盖</li>
 * </ul>
 */
public interface RepeatSubmitStore {

    /**
     * 尝试占位：在 intervalMillis 内首次调用返回 true，重复调用返回 false。
     */
    boolean tryAcquire(String key, long intervalMillis);
}
