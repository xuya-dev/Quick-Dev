package dev.xuya.core.datascope;

import java.util.Collection;

/**
 * 数据权限 SPI：返回当前用户在指定列上的可见值范围。
 * <p>实现并注册为 Bean 即对标注 {@link DataScope} 的实体生效，
 * 典型实现：查用户部门/区域，返回本部门 ID 集合；管理员返回 null 放开全部。</p>
 */
public interface DataScopeResolver {

    /**
     * @param entityClass 标注了 @DataScope 的实体类
     * @param column      @DataScope 声明的列名
     * @param currentUser 当前登录人（AuthContext，未登录为 null）
     * @return 可见值集合（追加 column IN (...) 条件）；null 表示不限制
     */
    Collection<?> visibleScope(Class<?> entityClass, String column, Object currentUser);
}
