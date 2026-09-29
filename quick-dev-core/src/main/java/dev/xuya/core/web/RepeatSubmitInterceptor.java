package dev.xuya.core.web;

import dev.xuya.core.auth.AuthContext;
import dev.xuya.core.auth.NoRepeatSubmit;
import dev.xuya.core.common.ParamException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 防重复提交拦截器：同一用户（token/loginId，匿名按 IP）+ 同一 HTTP 方法 + 同一 URI
 * 在注解声明的间隔内只放行一次。
 *
 * <p>实现为进程内 ConcurrentHashMap 时间戳（零依赖）；集群部署可自定义本拦截器替换为 Redis 实现。</p>
 */
public class RepeatSubmitInterceptor implements HandlerInterceptor {

    private static final int CLEAN_THRESHOLD = 10_000;

    private final Map<String, Long> lastSubmit = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        NoRepeatSubmit annotation = handlerMethod.getMethodAnnotation(NoRepeatSubmit.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), NoRepeatSubmit.class);
        }
        if (annotation == null) {
            return true;
        }

        String key = identity(request) + ":" + request.getMethod() + ":" + request.getRequestURI();
        long now = System.currentTimeMillis();
        long interval = annotation.interval();

        Long previous = lastSubmit.put(key, now);
        if (previous != null && now - previous < interval) {
            throw new ParamException("操作过于频繁，请勿重复提交");
        }
        if (lastSubmit.size() > CLEAN_THRESHOLD) {
            clean(now, interval);
        }
        return true;
    }

    private String identity(HttpServletRequest request) {
        String token = AuthContext.getToken();
        if (token != null && !token.isBlank()) {
            return "t:" + token;
        }
        Object user = AuthContext.getUser();
        if (user != null) {
            return "u:" + user;
        }
        return "ip:" + request.getRemoteAddr();
    }

    /** 惰性清理过期指纹，避免长期运行内存增长 */
    private void clean(long now, long maxInterval) {
        Iterator<Map.Entry<String, Long>> iterator = lastSubmit.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (now - entry.getValue() > maxInterval) {
                iterator.remove();
            }
        }
    }
}
