package dev.xuya.core.auth;

import dev.xuya.core.common.QuickDevException;
import dev.xuya.core.crud.QuickCrudHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 鉴权拦截器：
 * <ol>
 *   <li>放行无任何鉴权要求的接口；</li>
 *   <li>接口标注 {@link RequiresLogin}/{@link RequiresPerm}，或命中 {@link QuickCrudHandler}
 *       动态注册的 CRUD 端点（配置了权限码）时进入鉴权；</li>
 *   <li>解析 token -> {@link UserResolver} 得到当前用户，未登录抛 401；</li>
 *   <li>{@link PermissionChecker} 校验权限码，不通过抛 403。</li>
 * </ol>
 */
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthSettings settings;
    private final ApplicationContext applicationContext;
    private volatile UserResolver userResolver;
    private volatile PermissionChecker permissionChecker;
    private volatile boolean resolved = false;
    private volatile boolean warned = false;

    public AuthInterceptor(AuthSettings settings, ApplicationContext applicationContext) {
        this.settings = settings;
        this.applicationContext = applicationContext;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequiresPerm requiresPerm = findAnnotation(handlerMethod, RequiresPerm.class);
        boolean crudProtected = false;
        if (requiresPerm == null && handlerMethod.getBean() instanceof QuickCrudHandler crudHandler) {
            crudProtected = crudHandler.getRequiredPermission(handlerMethod.getMethod()) != null;
        }
        boolean needLogin = requiresPerm != null
                || crudProtected
                || findAnnotation(handlerMethod, RequiresLogin.class) != null;
        if (!needLogin) {
            return true;
        }

        if (!settings.isEnabled()) {
            return true; // 全局开关关闭时直接放行
        }

        Object user = resolveUser(request);
        if (user == null) {
            throw new AuthException("未登录或登录已过期");
        }
        AuthContext.set(user, resolveToken(request));

        PermissionChecker checker = getPermissionChecker();
        if (requiresPerm != null) {
            for (String code : requiresPerm.value()) {
                if (!checker.hasPermission(user, code)) {
                    throw new ForbiddenException("无操作权限: " + code);
                }
            }
        } else if (crudProtected) {
            String code = ((QuickCrudHandler) handlerMethod.getBean())
                    .getRequiredPermission(handlerMethod.getMethod());
            if (!checker.hasPermission(user, code)) {
                throw new ForbiddenException("无操作权限: " + code);
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuthContext.clear();
    }

    private <A extends java.lang.annotation.Annotation> A findAnnotation(HandlerMethod handlerMethod, Class<A> type) {
        A annotation = handlerMethod.getMethodAnnotation(type);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), type);
        }
        return annotation;
    }

    private Object resolveUser(HttpServletRequest request) {
        UserResolver resolver = getUserResolver();
        if (resolver == null) {
            throw new QuickDevException("接口需要鉴权，但容器中未找到 UserResolver 实现，请实现并注册该 Bean");
        }
        return resolver.getUser(resolveToken(request));
    }

    private String resolveToken(HttpServletRequest request) {
        String token = request.getHeader(settings.getTokenHeader());
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || token.isEmpty()) {
            token = request.getParameter(settings.getTokenParam());
        }
        return token;
    }

    private UserResolver getUserResolver() {
        ensureResolved();
        return userResolver;
    }

    private PermissionChecker getPermissionChecker() {
        ensureResolved();
        if (permissionChecker == null) {
            throw new QuickDevException("接口需要权限校验，但容器中未找到 PermissionChecker 实现，请实现并注册该 Bean");
        }
        return permissionChecker;
    }

    private void ensureResolved() {
        if (!resolved) {
            synchronized (this) {
                if (!resolved) {
                    userResolver = applicationContext.getBeanProvider(UserResolver.class).getIfAvailable();
                    permissionChecker = applicationContext.getBeanProvider(PermissionChecker.class).getIfAvailable();
                    if (userResolver == null && !warned) {
                        warned = true;
                        org.slf4j.LoggerFactory.getLogger(AuthInterceptor.class)
                                .warn("未找到 UserResolver 实现，鉴权注解将无法工作");
                    }
                    resolved = true;
                }
            }
        }
    }

}
