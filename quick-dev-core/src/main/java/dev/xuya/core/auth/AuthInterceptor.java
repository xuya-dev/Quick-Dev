package dev.xuya.core.auth;

import dev.xuya.core.common.QuickDevException;
import dev.xuya.core.crud.QuickCrudHandler;
import dev.xuya.core.methodop.QuickMethodOps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import java.util.Arrays;
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
    private volatile RoleChecker roleChecker;
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
        RequiresRole requiresRole = findAnnotation(handlerMethod, RequiresRole.class);
        boolean crudProtected = false;
        boolean crudLoginRequired = false;
        if (requiresPerm == null && handlerMethod.getBean() instanceof QuickCrudHandler crudHandler) {
            crudProtected = crudHandler.getRequiredPermission(handlerMethod.getMethod()) != null;
            // @QuickCrud(loginRequired = true) 且该端点无权限码时，仍要求登录
            crudLoginRequired = !crudProtected && crudHandler.isLoginRequired();
        }
        // 方法级注解（@QuickSave/@QuickUpdate/@QuickRemove/@QuickExport/@QuickImport）声明的权限码
        String methodOpPermission = requiresPerm == null && !crudProtected
                ? QuickMethodOps.permissionOf(handlerMethod.getMethod()) : null;
        boolean needLogin = requiresPerm != null
                || requiresRole != null
                || crudProtected
                || crudLoginRequired
                || methodOpPermission != null
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

        if (requiresPerm != null) {
            PermissionChecker checker = getPermissionChecker();
            for (String code : requiresPerm.value()) {
                if (!checker.hasPermission(user, code)) {
                    throw new ForbiddenException("无操作权限: " + code);
                }
            }
        } else if (crudProtected) {
            PermissionChecker checker = getPermissionChecker();
            String code = ((QuickCrudHandler) handlerMethod.getBean())
                    .getRequiredPermission(handlerMethod.getMethod());
            if (!checker.hasPermission(user, code)) {
                throw new ForbiddenException("无操作权限: " + code);
            }
        } else if (methodOpPermission != null) {
            PermissionChecker checker = getPermissionChecker();
            if (!checker.hasPermission(user, methodOpPermission)) {
                throw new ForbiddenException("无操作权限: " + methodOpPermission);
            }
        }

        if (requiresRole != null) {
            RoleChecker roleChecker = getRoleChecker();
            boolean pass = Logical.OR == requiresRole.logical()
                    ? Arrays.stream(requiresRole.value()).anyMatch(r -> roleChecker.hasRole(user, r))
                    : Arrays.stream(requiresRole.value()).allMatch(r -> roleChecker.hasRole(user, r));
            if (!pass) {
                throw new ForbiddenException("缺少所需角色: " + String.join(", ", requiresRole.value()));
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuthContext.clear();
    }

    private <A extends Annotation> A findAnnotation(HandlerMethod handlerMethod, Class<A> type) {
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

    private RoleChecker getRoleChecker() {
        ensureResolved();
        if (roleChecker == null) {
            throw new QuickDevException("接口需要角色校验，但容器中未找到 RoleChecker 实现，请实现并注册该 Bean");
        }
        return roleChecker;
    }

    private void ensureResolved() {
        if (!resolved) {
            synchronized (this) {
                if (!resolved) {
                    userResolver = applicationContext.getBeanProvider(UserResolver.class).getIfAvailable();
                    permissionChecker = applicationContext.getBeanProvider(PermissionChecker.class).getIfAvailable();
                    roleChecker = applicationContext.getBeanProvider(RoleChecker.class).getIfAvailable();
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
