package dev.xuya.core.auth;

/**
 * 角色判断 SPI。
 * <p>内置 Sa-Token 桥接下自动实现为 StpUtil.hasRole(loginId, role)；
 * 角色数据来自 Sa-Token 的 StpInterface.getRoleList。未使用 Sa-Token 时自行实现并注册 Bean。</p>
 */
public interface RoleChecker {

    /**
     * @param user {@link UserResolver} 解析出的当前用户（Sa-Token 模式下为 loginId）
     * @param role 角色码，例如 "admin"
     * @return 是否拥有该角色
     */
    boolean hasRole(Object user, String role);
}
