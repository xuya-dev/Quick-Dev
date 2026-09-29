package dev.xuya.core.auth;

/**
 * 权限判断 SPI。
 * <p>由业务方实现并注册为 Spring Bean，通常对接自己的 RBAC 表（用户-角色-权限）。</p>
 */
public interface PermissionChecker {

    /**
     * @param user       {@link UserResolver} 解析出的当前用户对象
     * @param permission 权限码，例如 "sys:user:list"
     * @return 是否拥有该权限
     */
    boolean hasPermission(Object user, String permission);
}
