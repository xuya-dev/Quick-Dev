package dev.xuya.core.auth;

/**
 * 当前登录用户上下文（ThreadLocal，请求结束自动清理）。
 * <p>在业务代码中随时可取：{@code AuthContext.getUser()}</p>
 */
public final class AuthContext {

    private static final ThreadLocal<Object> USER = new ThreadLocal<>();
    private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(Object user, String token) {
        USER.set(user);
        TOKEN.set(token);
    }

    /** @return 当前登录用户（未登录为 null） */
    @SuppressWarnings("unchecked")
    public static <T> T getUser() {
        return (T) USER.get();
    }

    /** @return 当前请求的令牌（未登录为 null） */
    public static String getToken() {
        return TOKEN.get();
    }

    public static void clear() {
        USER.remove();
        TOKEN.remove();
    }
}
