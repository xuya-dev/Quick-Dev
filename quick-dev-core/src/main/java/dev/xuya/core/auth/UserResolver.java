package dev.xuya.core.auth;

/**
 * 令牌 -> 当前用户 的解析 SPI。
 * <p>由业务方实现并注册为 Spring Bean：拦截器取出请求头中的 token 后调用本方法得到当前用户对象，
 * 该对象随后传入 {@link PermissionChecker} 做权限判断，也会存入 {@link AuthContext}。</p>
 * <p>返回 null 表示未登录（token 无效或过期）。</p>
 */
public interface UserResolver {

    /**
     * @param token 请求中的令牌（已去除 Bearer 前缀）
     * @return 当前用户对象，未登录返回 null
     */
    Object getUser(String token);
}
