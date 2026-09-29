package dev.xuya.demo.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import dev.xuya.core.auth.PermissionChecker;
import dev.xuya.core.auth.UserResolver;
import dev.xuya.core.common.QuickDevException;
import dev.xuya.demo.entity.SysUser;
import dev.xuya.demo.entity.SysUserPerm;
import dev.xuya.demo.mapper.SysUserMapper;
import dev.xuya.demo.mapper.SysUserPermMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 框架鉴权 SPI 的示例实现：内存 token + 数据库权限表。
 * <p>生产环境替换为 Redis token + 完整 RBAC 即可，框架不感知实现细节。</p>
 */
@Service
public class DbAuthService implements UserResolver, PermissionChecker {

    private final SysUserMapper userMapper;
    private final SysUserPermMapper permMapper;

    /** token -> userId（演示用内存存储） */
    private final Map<String, Long> tokens = new ConcurrentHashMap<>();

    public DbAuthService(SysUserMapper userMapper, SysUserPermMapper permMapper) {
        this.userMapper = userMapper;
        this.permMapper = permMapper;
    }

    public Map<String, Object> login(String username, String password) {
        SysUser user = userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", username));
        if (user == null || !password.equals(user.getPassword())) {
            throw new QuickDevException("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new QuickDevException("账号已被停用");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, user.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", user);
        return result;
    }

    public void logout(String token) {
        if (token != null) {
            tokens.remove(token);
        }
    }

    @Override
    public Object getUser(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Long userId = tokens.get(token);
        return userId == null ? null : userMapper.selectById(userId);
    }

    @Override
    public boolean hasPermission(Object user, String permission) {
        SysUser sysUser = (SysUser) user;
        List<SysUserPerm> perms = permMapper.selectList(
                new QueryWrapper<SysUserPerm>().eq("user_id", sysUser.getId()));
        return perms.stream().anyMatch(p -> "*".equals(p.getPermCode()) || p.getPermCode().equals(permission));
    }
}
