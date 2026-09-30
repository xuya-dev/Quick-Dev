package dev.xuya.core.auth;

import dev.xuya.core.common.QuickDevException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AuthInterceptor 行为与"身份泄漏"回归：
 * 403 等拒绝路径不得把用户身份遗留在 Tomcat 线程上（AuthContext 必须在全部校验通过后才写入）。
 */
class AuthInterceptorTest {

    private final UserResolver resolver = mock(UserResolver.class);
    private final PermissionChecker checker = mock(PermissionChecker.class);
    private final RoleChecker roleChecker = mock(RoleChecker.class);
    private final AuthSettings settings = new AuthSettings(true, "Authorization", "token");
    private final AuthInterceptor interceptor = new AuthInterceptor(settings, stubContext());

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void stubResolver() {
        when(resolver.getUser(anyString())).thenAnswer(inv -> {
            String token = inv.getArgument(0);
            return token == null ? null : "user-of-" + token;
        });
        when(checker.hasPermission(anyString(), anyString())).thenReturn(true);
        when(roleChecker.hasRole(anyString(), anyString())).thenReturn(true);
    }

    @AfterEach
    void cleanup() {
        AuthContext.clear();
    }

    private ApplicationContext stubContext() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        ObjectProvider<UserResolver> resolverProvider = mock(ObjectProvider.class);
        ObjectProvider<PermissionChecker> checkerProvider = mock(ObjectProvider.class);
        ObjectProvider<RoleChecker> roleProvider = mock(ObjectProvider.class);
        when(ctx.getBeanProvider(UserResolver.class)).thenReturn(resolverProvider);
        when(ctx.getBeanProvider(PermissionChecker.class)).thenReturn(checkerProvider);
        when(ctx.getBeanProvider(RoleChecker.class)).thenReturn(roleProvider);
        when(resolverProvider.getIfAvailable()).thenReturn(resolver);
        when(checkerProvider.getIfAvailable()).thenReturn(checker);
        when(roleProvider.getIfAvailable()).thenReturn(roleChecker);
        return ctx;
    }

    // ------------------------------------------------------------------
    // 无鉴权要求：直接放行，不写 AuthContext
    // ------------------------------------------------------------------

    @Test
    void handlerWithoutAnnotationShouldPassThrough() throws Exception {
        boolean pass = interceptor.preHandle(request("t1"), response, handler("noAuth"));
        assertThat(pass).isTrue();
        assertThat((Object) AuthContext.getUser()).isNull();
    }

    // ------------------------------------------------------------------
    // 关键回归：权限拒绝时 AuthContext 必须保持 null
    // ------------------------------------------------------------------

    @Test
    void permissionDeniedShouldThrowAndNeverLeakIdentity() {
        when(checker.hasPermission(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preHandle(request("t1"), response, handler("withPerm")))
                .isInstanceOf(ForbiddenException.class);

        // 回归断言：拒绝路径不遗留身份到线程（修复前此处会读到 "user-of-t1"）
        assertThat((Object) AuthContext.getUser()).isNull();
        assertThat(AuthContext.getToken()).isNull();
    }

    @Test
    void roleDeniedShouldThrowAndNeverLeakIdentity() {
        when(roleChecker.hasRole(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preHandle(request("t1"), response, handler("withRole")))
                .isInstanceOf(ForbiddenException.class);

        assertThat((Object) AuthContext.getUser()).isNull();
    }

    @Test
    void unauthenticatedShouldThrow401AndNotSetContext() {
        when(resolver.getUser(anyString())).thenReturn(null);

        assertThatThrownBy(() -> interceptor.preHandle(request(null), response, handler("withPerm")))
                .isInstanceOf(AuthException.class);

        assertThat((Object) AuthContext.getUser()).isNull();
    }

    // ------------------------------------------------------------------
    // 校验全部通过后才写上下文
    // ------------------------------------------------------------------

    @Test
    void permissionGrantedShouldSetContextAndPass() throws Exception {
        boolean pass = interceptor.preHandle(request("t1"), response, handler("withPerm"));

        assertThat(pass).isTrue();
        assertThat((Object) AuthContext.getUser()).isEqualTo("user-of-t1");
        assertThat(AuthContext.getToken()).isEqualTo("t1");

        interceptor.afterCompletion(request("t1"), response, handler("withPerm"), null);
        assertThat((Object) AuthContext.getUser()).isNull();
    }

    // ------------------------------------------------------------------
    // 角色语义：OR 任一满足 / AND 全部满足
    // ------------------------------------------------------------------

    @Test
    void roleOrShouldPassWhenAnyRoleMatches() throws Exception {
        when(roleChecker.hasRole(anyString(), anyString())).thenReturn(false);
        when(roleChecker.hasRole(anyString(), eq("admin"))).thenReturn(true);

        boolean pass = interceptor.preHandle(request("t1"), response, handler("roleOr"));
        assertThat(pass).isTrue();
        assertThat((Object) AuthContext.getUser()).isEqualTo("user-of-t1");
    }

    @Test
    void roleAndShouldFailWhenAnyRoleMissing() {
        when(roleChecker.hasRole(anyString(), anyString())).thenReturn(true);
        when(roleChecker.hasRole(anyString(), eq("viewer"))).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preHandle(request("t1"), response, handler("roleAnd")))
                .isInstanceOf(ForbiddenException.class);
        assertThat((Object) AuthContext.getUser()).isNull();
    }

    // ------------------------------------------------------------------
    // 全局开关关闭：带注解也直接放行
    // ------------------------------------------------------------------

    @Test
    void authDisabledShouldBypassAllChecks() throws Exception {
        AuthInterceptor disabled = new AuthInterceptor(new AuthSettings(false, "Authorization", "token"), stubContext());
        boolean pass = disabled.preHandle(request(null), response, handler("withPerm"));
        assertThat(pass).isTrue();
        assertThat((Object) AuthContext.getUser()).isNull();
    }

    // ------------------------------------------------------------------
    // token 解析：Bearer 前缀剥离 + 缺失 UserResolver 失败关闭
    // ------------------------------------------------------------------

    @Test
    void bearerPrefixShouldBeStripped() throws Exception {
        MockHttpServletRequest req = request(null);
        req.addHeader("Authorization", "Bearer abc123");
        interceptor.preHandle(req, response, handler("withPerm"));
        String user = AuthContext.getUser();
        assertThat(user).isEqualTo("user-of-abc123");
    }

    @Test
    void missingUserResolverShouldFailClosedWithConfigError() {
        ApplicationContext emptyCtx = mock(ApplicationContext.class);
        ObjectProvider<UserResolver> emptyResolverProvider = mock(ObjectProvider.class);
        ObjectProvider<PermissionChecker> emptyCheckerProvider = mock(ObjectProvider.class);
        ObjectProvider<RoleChecker> emptyRoleProvider = mock(ObjectProvider.class);
        when(emptyCtx.getBeanProvider(UserResolver.class)).thenReturn(emptyResolverProvider);
        when(emptyCtx.getBeanProvider(PermissionChecker.class)).thenReturn(emptyCheckerProvider);
        when(emptyCtx.getBeanProvider(RoleChecker.class)).thenReturn(emptyRoleProvider);
        when(emptyResolverProvider.getIfAvailable()).thenReturn(null);

        AuthInterceptor noResolver = new AuthInterceptor(settings, emptyCtx);
        assertThatThrownBy(() -> noResolver.preHandle(request("t1"), response, handler("withPerm")))
                .isInstanceOf(QuickDevException.class)
                .hasMessageContaining("UserResolver");
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/x");
        if (token != null) {
            req.addHeader("Authorization", token);
        }
        return req;
    }

    private HandlerMethod handler(String methodName) throws NoSuchMethodException {
        Method method = SampleController.class.getMethod(methodName);
        return new HandlerMethod(new SampleController(), method);
    }

    static class SampleController {

        public void noAuth() {
        }

        @RequiresPerm("a:b")
        public void withPerm() {
        }

        @RequiresRole("admin")
        public void withRole() {
        }

        @RequiresRole(value = {"admin", "auditor"}, logical = Logical.OR)
        public void roleOr() {
        }

        @RequiresRole(value = {"admin", "viewer"}, logical = Logical.AND)
        public void roleAnd() {
        }
    }
}
