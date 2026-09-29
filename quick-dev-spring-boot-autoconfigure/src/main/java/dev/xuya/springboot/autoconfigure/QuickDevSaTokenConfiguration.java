package dev.xuya.springboot.autoconfigure;

import cn.dev33.satoken.stp.StpUtil;
import dev.xuya.core.auth.PermissionChecker;
import dev.xuya.core.auth.RoleChecker;
import dev.xuya.core.auth.UserResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Sa-Token 集成：classpath 存在 Sa-Token 且用户未自定义 {@link UserResolver}/{@link PermissionChecker}
 * 时，自动把两个鉴权 SPI 桥接到 Sa-Token：
 * <ul>
 *   <li>登录态：token -> StpUtil.getLoginIdByToken(token)（未登录返回 null，AuthContext.getUser() 即 loginId）</li>
 *   <li>权限码：StpUtil.hasPermission(loginId, code)，权限数据来自用户实现的 Sa-Token StpInterface</li>
 *   <li>角色：StpUtil.hasRole(loginId, role)，角色数据来自 StpInterface.getRoleList</li>
 * </ul>
 * <p>即：登录用 StpUtil.login(userId)，权限/角色查询实现 StpInterface，框架的 @RequiresPerm /
 * @RequiresRole / @QuickCrud 权限码校验自动打通。</p>
 */
@AutoConfiguration
@ConditionalOnClass(StpUtil.class)
public class QuickDevSaTokenConfiguration {

    @Bean
    @ConditionalOnMissingBean(UserResolver.class)
    public UserResolver saTokenUserResolver() {
        return StpUtil::getLoginIdByToken;
    }

    @Bean
    @ConditionalOnMissingBean(PermissionChecker.class)
    public PermissionChecker saTokenPermissionChecker() {
        return StpUtil::hasPermission;
    }

    @Bean
    @ConditionalOnMissingBean(RoleChecker.class)
    public RoleChecker saTokenRoleChecker() {
        return StpUtil::hasRole;
    }
}
