package dev.xuya.core.web;

import dev.xuya.core.auth.AuthException;
import dev.xuya.core.auth.ForbiddenException;
import dev.xuya.core.common.ParamException;
import dev.xuya.core.common.QuickDevException;
import dev.xuya.core.common.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常 -> 统一响应 R。
 * <p>鉴权失败返回真实 HTTP 状态码（401/403），业务/参数错误 HTTP 200 + code。</p>
 * <p>本 Advice 优先级最低：用户应用自定义的 @RestControllerAdvice 优先生效。</p>
 * <p>{@code errorDetail=false}（quick-dev.error-detail）时，未预期异常不透出内部信息，
 * 只返回"系统繁忙"（日志仍完整记录）。</p>
 */
@Order
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final boolean errorDetail;

    public GlobalExceptionHandler() {
        this(true);
    }

    public GlobalExceptionHandler(boolean errorDetail) {
        this.errorDetail = errorDetail;
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<R<Void>> handleAuth(AuthException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(R.fail(401, e.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<R<Void>> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(R.fail(403, e.getMessage()));
    }

    @ExceptionHandler(ParamException.class)
    public R<Void> handleParam(ParamException e) {
        return R.fail(400, e.getMessage());
    }

    @ExceptionHandler(QuickDevException.class)
    public R<Void> handleQuickDev(QuickDevException e) {
        log.warn("业务异常: {}", e.getMessage());
        return R.fail(500, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return R.fail(500, errorDetail ? "系统异常: " + e.getMessage() : "系统繁忙，请稍后重试");
    }
}
