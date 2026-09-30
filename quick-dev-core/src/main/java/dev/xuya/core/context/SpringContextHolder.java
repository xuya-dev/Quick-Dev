package dev.xuya.core.context;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationContextException;

/**
 * Spring 容器静态持有器：供 Jackson 序列化器等非 Spring 管理的组件获取 Bean。
 * 由自动配置注册；未初始化时 getBean 抛出明确异常。
 */
public class SpringContextHolder implements ApplicationContextAware {

    private static volatile ApplicationContext context;

    public static ApplicationContext getContext() {
        return context;
    }

    public static <T> T getBean(Class<T> type) {
        ApplicationContext ctx = context;
        if (ctx == null) {
            throw new ApplicationContextException("SpringContextHolder 尚未初始化（容器未启动）");
        }
        return ctx.getBean(type);
    }

    public static <T> T getBeanIfAvailable(Class<T> type) {
        ApplicationContext ctx = context;
        return ctx == null ? null : ctx.getBeanProvider(type).getIfAvailable();
    }

    /**
     * 按 @Order 顺序返回某类型全部 Bean 的流（无 Bean 时为空流）。
     * 供 SPI 的"多实现链式取第一个非 null"场景使用（如 {@code TranslateSource}）。
     */
    public static <T> java.util.stream.Stream<T> getBeansOrdered(Class<T> type) {
        ApplicationContext ctx = context;
        return ctx == null ? java.util.stream.Stream.empty() : ctx.getBeanProvider(type).orderedStream();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }
}
