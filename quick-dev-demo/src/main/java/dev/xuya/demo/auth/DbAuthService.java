package dev.xuya.demo.auth;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import dev.xuya.core.common.QuickDevException;
import dev.xuya.demo.entity.SysUser;
import dev.xuya.demo.entity.SysUserPerm;
import dev.xuya.demo.entity.SysUserRole;
import dev.xuya.demo.mapper.SysUserMapper;
import dev.xuya.demo.mapper.SysUserPermMapper;
import dev.xuya.demo.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sa-Token 集成示例：登录直接用 StpUtil.login，权限/角色数据通过 StpInterface 提供。
 * <p>框架内置的 UserResolver/PermissionChecker/RoleChecker 桥接会自动接管 token 校验、
 * hasPermission 与 hasRole，@RequiresPerm / @RequiresRole / @QuickCrud 校验无需额外代码。</p>
 */
@Service
public class DbAuthService implements StpInterface {

    private final SysUserMapper userMapper;
    private final SysUserPermMapper permMapper;
    private final SysUserRoleMapper roleMapper;

    public DbAuthService(SysUserMapper userMapper, SysUserPermMapper permMapper, SysUserRoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.permMapper = permMapper;
        this.roleMapper = roleMapper;
    }

    public Map<String, Object> login(String username, String password) {
        SysUser user = userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", username));
        if (user == null || !password.equals(user.getPassword())) {
            throw new QuickDevException("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new QuickDevException("账号已被停用");
        }
        StpUtil.login(user.getId());
        Map<String, Object> result = new HashMap<>();
        result.put("token", StpUtil.getTokenValue());
        result.put("user", user);
        return result;
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            StpUtil.logoutByTokenValue(token);
        }
    }

    public SysUser currentUser() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        return loginId == null ? null : userMapper.selectById(Long.valueOf(loginId.toString()));
    }

    /**
     * Sa-Token 权限数据源：查询用户的权限码列表（* 表示超级权限）
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return permMapper.selectList(new QueryWrapper<SysUserPerm>()
                        .eq("user_id", Long.valueOf(loginId.toString())))
                .stream().map(SysUserPerm::getPermCode).toList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return roleMapper.selectList(new QueryWrapper<SysUserRole>()
                        .eq("user_id", Long.valueOf(loginId.toString())))
                .stream().map(SysUserRole::getRoleCode).toList();
    }
}
