package dev.xuya.core.web;

import dev.xuya.core.auth.AuthException;
import dev.xuya.core.auth.ForbiddenException;
import dev.xuya.core.common.ParamException;
import dev.xuya.core.common.QuickDevException;
import dev.xuya.core.common.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常 -> 统一响应 R。
 * <p>鉴权失败返回真实 HTTP 状态码（401/403），业务/参数错误保持 HTTP 200 + 业务码
 * （本框架的既定契约，前端按 body.code 判定）。</p>
 *
 * <p><b>排序与让位</b>：显式声明 {@code @Order(Ordered.LOWEST_PRECEDENCE)}——
 * 语义为"用户应用自定义的 @RestControllerAdvice 优先匹配"。Spring 对相同 order 的
 * Advice 按注册顺序匹配（用户组件先于自动配置注册），因此自定义 Advice 会先接手；
 * 若你的 Advice 显式声明了更小的 order 值则优先级更稳。</p>
 *
 * <p>Spring MVC 自身的资源异常（404/405）不吞：交给框架默认处理返回真实状态码，
 * 而不是被兜底 handler 包装成业务错误。</p>
 *
 * <p>{@code errorDetail=false}（quick-dev.error-detail）时，未预期异常不透出内部信息，
 * 只返回"系统繁忙"（日志仍完整记录）。</p>
 */
@Order(Ordered.LOWEST_PRECEDENCE)
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

    /**
     * 唯一键冲突（新增/修改违反唯一索引）：转成友好 400，不透出 SQL 细节
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public R<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一键冲突: {}", e.getMessage());
        return R.fail(400, "保存失败，唯一键冲突：请检查关键字段是否已存在相同记录");
    }

    @ExceptionHandler(QuickDevException.class)
    public R<Void> handleQuickDev(QuickDevException e) {
        log.warn("业务异常: {}", e.getMessage());
        return R.fail(500, e.getMessage());
    }

    /**
     * 路径不存在：返回真实 404（此前被兜底 handler 包成 HTTP 200 + code 500，
     * 网关与浏览器无法按状态码处理，扫描器还会把全站 404 误判成 200）
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<R<Void>> handleNotFound(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(R.fail(404, "请求的资源不存在"));
    }

    /**
     * HTTP 方法不支持：返回真实 405（同理不再包装为业务错误）
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<R<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(R.fail(405, "不支持的请求方法: " + e.getMethod()));
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return R.fail(500, errorDetail ? "系统异常: " + e.getMessage() : "系统繁忙，请稍后重试");
    }
}
