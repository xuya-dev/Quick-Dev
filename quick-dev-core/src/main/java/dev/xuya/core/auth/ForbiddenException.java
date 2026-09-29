package dev.xuya.core.auth;

import dev.xuya.core.common.QuickDevException;

/**
 * 已登录但权限不足，HTTP 403。
 */
public class ForbiddenException extends QuickDevException {

    public ForbiddenException(String message) {
        super(message);
    }
}
