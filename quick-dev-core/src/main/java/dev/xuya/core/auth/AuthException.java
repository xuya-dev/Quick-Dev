package dev.xuya.core.auth;

import dev.xuya.core.common.QuickDevException;

/**
 * 未登录 / 登录过期，HTTP 401。
 */
public class AuthException extends QuickDevException {

    public AuthException(String message) {
        super(message);
    }
}
