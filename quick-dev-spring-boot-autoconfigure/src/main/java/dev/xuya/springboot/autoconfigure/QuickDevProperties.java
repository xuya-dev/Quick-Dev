package dev.xuya.springboot.autoconfigure;

import com.baomidou.mybatisplus.annotation.DbType;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 配置项（前缀 quick-dev）：
 * <pre>
 * quick-dev:
 *   enabled: true          # CRUD 引擎总开关
 *   db-type: h2            # 分页插件数据库方言（可选）
 *   auth:
 *     enabled: true        # 鉴权总开关
 *     token-header: Authorization
 *     token-param: token
 *   auto-fill:
 *     enabled: true        # createTime/updateTime 自动填充开关
 * </pre>
 */
@ConfigurationProperties(prefix = "quick-dev")
public class QuickDevProperties {

    /** 是否启用 @QuickCrud 动态端点注册 */
    private boolean enabled = true;

    /** 分页插件方言（MyBatis-Plus DbType 名称，如 mysql / h2 / postgresql），留空自动 */
    private DbType dbType;

    private final Auth auth = new Auth();
    private final AutoFill autoFill = new AutoFill();

    public static class AutoFill {

        /** 是否启用 createTime/updateTime 自动填充 */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Auth {

        /** 鉴权总开关，false 时所有鉴权注解直接放行 */
        private boolean enabled = true;

        /** 携带 token 的请求头 */
        private String tokenHeader = "Authorization";

        /** 兜底：携带 token 的请求参数名 */
        private String tokenParam = "token";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTokenHeader() {
            return tokenHeader;
        }

        public void setTokenHeader(String tokenHeader) {
            this.tokenHeader = tokenHeader;
        }

        public String getTokenParam() {
            return tokenParam;
        }

        public void setTokenParam(String tokenParam) {
            this.tokenParam = tokenParam;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public DbType getDbType() {
        return dbType;
    }

    public void setDbType(DbType dbType) {
        this.dbType = dbType;
    }

    public Auth getAuth() {
        return auth;
    }

    public AutoFill getAutoFill() {
        return autoFill;
    }
}
