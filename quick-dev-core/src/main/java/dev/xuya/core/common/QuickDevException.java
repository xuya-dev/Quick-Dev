package dev.xuya.core.common;

/**
 * 框架基础异常，业务/框架运行时错误统一携带用户可读信息。
 */
public class QuickDevException extends RuntimeException {

    public QuickDevException(String message) {
        super(message);
    }

    public QuickDevException(String message, Throwable cause) {
        super(message, cause);
    }
}
