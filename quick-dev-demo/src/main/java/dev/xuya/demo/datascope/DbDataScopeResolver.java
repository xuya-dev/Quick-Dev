package dev.xuya.demo.datascope;

import dev.xuya.core.datascope.DataScopeResolver;
import dev.xuya.demo.entity.SysUser;
import dev.xuya.demo.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * 数据权限示例：admin（loginId=1）不限制；其他用户只能看到本部门数据。
 * <p>生产环境替换为"查用户部门集合 + 子部门递归"等业务规则即可。</p>
 */
@Service
public class DbDataScopeResolver implements DataScopeResolver {

    private final SysUserMapper userMapper;

    public DbDataScopeResolver(SysUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public Collection<?> visibleScope(Class<?> entityClass, String column, Object currentUser) {
        if (currentUser == null) {
            return List.of(); // 未登录：不展示任何数据（开放接口可按需返回 null 放开）
        }
        Long loginId = Long.valueOf(currentUser.toString());
        if (loginId == 1L) {
            return null; // admin 不限制
        }
        SysUser user = userMapper.selectById(loginId);
        if (user == null || user.getDeptId() == null) {
            return List.of();
        }
        return List.of(user.getDeptId()); // 只看本部门
    }
}
