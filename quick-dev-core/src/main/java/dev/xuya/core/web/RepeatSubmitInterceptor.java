package dev.xuya.core.web;

import dev.xuya.core.auth.AuthContext;
import dev.xuya.core.auth.NoRepeatSubmit;
import dev.xuya.core.common.ParamException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.DigestUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 防重复提交拦截器：同一用户（token/loginId，匿名按 IP）+ 同一 HTTP 方法 + 同一 URI
 * 在注解声明的间隔内只放行一次。指纹存储由 {@link RepeatSubmitStore} 决定
 * （默认进程内存；classpath 有 Redis 时框架自动切换 Redis 原子实现）。
 */
public class RepeatSubmitInterceptor implements HandlerInterceptor {

    private final RepeatSubmitStore store;

    public RepeatSubmitInterceptor(RepeatSubmitStore store) {
        this.store = store;
    }

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
        if (!store.tryAcquire("quick-dev:repeat:" + key, annotation.interval())) {
            throw new ParamException("操作过于频繁，请勿重复提交");
        }
        return true;
    }

    private String identity(HttpServletRequest request) {
        String token = AuthContext.getToken();
        if (token != null && !token.isBlank()) {
            // 指纹存内存/Redis：token 只留哈希，避免明文凭据落入存储
            return "t:" + DigestUtils.md5DigestAsHex(token.getBytes(StandardCharsets.UTF_8));
        }
        Object user = AuthContext.getUser();
        if (user != null) {
            return "u:" + user;
        }
        return "ip:" + request.getRemoteAddr();
    }
}
