package dev.xuya.core.web;

import dev.xuya.core.auth.AuthException;
import dev.xuya.core.auth.ForbiddenException;
import dev.xuya.core.common.ParamException;
import dev.xuya.core.common.QuickDevException;
import dev.xuya.core.common.R;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全局异常 -> 统一响应 R 的映射契约：
 * 业务/参数错误 = HTTP 200 + 业务码；鉴权 = 真实 401/403；MVC 资源异常 = 真实 404/405。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(true);

    @Test
    void duplicateKeyShouldReturnFriendly400WithoutSqlDetail() {
        R<Void> result = handler.handleDuplicateKey(
                new DuplicateKeyException("Duplicate entry 'admin' for key 'uk_username'"));
        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMsg()).contains("唯一键冲突");
        // 不透出 SQL/索引细节
        assertThat(result.getMsg()).doesNotContain("Duplicate entry");
    }

    @Test
    void authFailureShouldMapToReal401And403() {
        ResponseEntity<R<Void>> auth = handler.handleAuth(new AuthException("未登录"));
        assertThat(auth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(auth.getBody().getCode()).isEqualTo(401);

        ResponseEntity<R<Void>> forbidden = handler.handleForbidden(new ForbiddenException("无权限"));
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbidden.getBody().getCode()).isEqualTo(403);
    }

    @Test
    void paramErrorShouldBeHttp200WithBusinessCode400() {
        R<Void> result = handler.handleParam(new ParamException("参数非法"));
        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMsg()).isEqualTo("参数非法");
    }

    @Test
    void businessExceptionShouldKeepMessageWithCode500() {
        R<Void> result = handler.handleQuickDev(new QuickDevException("用户名或密码错误"));
        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMsg()).isEqualTo("用户名或密码错误");
    }

    @Test
    void resourceNotFoundShouldBeReal404NotBusinessError() {
        NoResourceFoundException e = new NoResourceFoundException(HttpMethod.GET, "/no/such/path");
        ResponseEntity<R<Void>> result = handler.handleNotFound(e);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody().getCode()).isEqualTo(404);
        // 不把内部路径细节透给客户端
        assertThat(result.getBody().getMsg()).doesNotContain("/no/such/path");
    }

    @Test
    void methodNotSupportedShouldBeReal405() {
        HttpRequestMethodNotSupportedException e =
                new HttpRequestMethodNotSupportedException("PATCH");
        ResponseEntity<R<Void>> result = handler.handleMethodNotSupported(e);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(result.getBody().getCode()).isEqualTo(405);
    }

    @Test
    void unexpectedExceptionShouldRespectErrorDetailSwitch() {
        RuntimeException boom = new RuntimeException("数据库连接串 jdbc:xxx 已泄露");

        // errorDetail=true：透出原始信息
        R<Void> detailed = new GlobalExceptionHandler(true).handleOther(boom);
        assertThat(detailed.getCode()).isEqualTo(500);
        assertThat(detailed.getMsg()).contains("jdbc:xxx");

        // errorDetail=false：只返回通用提示
        R<Void> masked = new GlobalExceptionHandler(false).handleOther(boom);
        assertThat(masked.getCode()).isEqualTo(500);
        assertThat(masked.getMsg()).isEqualTo("系统繁忙，请稍后重试");
        assertThat(masked.getMsg()).doesNotContain("jdbc");
    }
}
