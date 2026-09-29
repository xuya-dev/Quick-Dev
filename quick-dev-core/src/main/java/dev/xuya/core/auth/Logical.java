package dev.xuya.core.auth;

/**
 * 多角色/多权限之间的组合逻辑。
 */
public enum Logical {

    /**
     * 全部满足
     */
    AND,
    /**
     * 任一满足
     */
    OR
}
