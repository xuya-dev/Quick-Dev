package dev.xuya.core.web;

import dev.xuya.core.auth.AuthContext;
import dev.xuya.core.auth.NoRepeatSubmit;
import dev.xuya.core.common.ParamException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 防重拦截器：指纹身份（匿名按 IP / 登录按 token 哈希）与注解窗口
 */
class RepeatSubmitInterceptorTest {

    private final RepeatSubmitInterceptor interceptor = new RepeatSubmitInterceptor(new MemoryRepeatSubmitStore());

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void cleanup() {
        AuthContext.clear();
    }

    @Test
    void handlerWithoutAnnotationShouldPassThrough() throws Exception {
        assertThatCode(() -> interceptor.preHandle(request(null), response, handler("noGuard")))
                .doesNotThrowAnyException();
    }

    @Test
    void anonymousRequestsShouldShareIpIdentity() {
        assertThatCode(() -> interceptor.preHandle(request(null), response, handler("guarded")))
                .doesNotThrowAnyException();
        // 同一匿名身份的第二次调用在窗口内被拒
        assertThatThrownBy(() -> interceptor.preHandle(request(null), response, handler("guarded")))
                .isInstanceOf(ParamException.class)
                .hasMessageContaining("重复提交");
    }

    @Test
    void differentAnonymousIpsShouldNotInterfere() {
        assertThatCode(() -> interceptor.preHandle(request(null, "1.1.1.1"), response, handler("guarded")))
                .doesNotThrowAnyException();
        assertThatCode(() -> interceptor.preHandle(request(null, "2.2.2.2"), response, handler("guarded")))
                .doesNotThrowAnyException();
    }

    @Test
    void loggedInIdentityUsesTokenHashNotRawToken() {
        // 登录态指纹来自 token 哈希：同一 token 二次被拒
        AuthContext.set("user1", "token-A");
        assertThatCode(() -> interceptor.preHandle(request("token-A"), response, handler("guarded")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> interceptor.preHandle(request("token-A"), response, handler("guarded")))
                .isInstanceOf(ParamException.class);
        AuthContext.clear();

        // 不同 token 是不同身份，互不影响
        AuthContext.set("user2", "token-B");
        assertThatCode(() -> interceptor.preHandle(request("token-B"), response, handler("guarded")))
                .doesNotThrowAnyException();
    }

    @Test
    void sameUriDifferentMethodsShouldBeIndependent() {
        MockHttpServletRequest get = request(null);
        get.setMethod("GET");
        MockHttpServletRequest post = request(null);
        post.setMethod("POST");
        assertThatCode(() -> interceptor.preHandle(get, response, handler("guarded")))
                .doesNotThrowAnyException();
        assertThatCode(() -> interceptor.preHandle(post, response, handler("guarded")))
                .doesNotThrowAnyException();
    }

    private MockHttpServletRequest request(String token) {
        return request(token, "127.0.0.1");
    }

    private MockHttpServletRequest request(String token, String remoteAddr) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/order");
        req.setRemoteAddr(remoteAddr);
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

        public void noGuard() {
        }

        @NoRepeatSubmit(interval = 10_000)
        public void guarded() {
        }
    }
}
