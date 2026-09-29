package dev.xuya.springboot.autoconfigure;

import com.baomidou.mybatisplus.annotation.DbType;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 配置项（前缀 quick-dev）：
 * <pre>
 * quick-dev:
 *   enabled: true          # CRUD 引擎总开关
 *   db-type: h2            # 分页插件数据库方言（可选）
 *   method-op:
 *     enabled: true        # 方法级注解（@QuickSave 等 AOP 接管）开关
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
    private final MethodOp methodOp = new MethodOp();
    private final RepeatSubmit repeatSubmit = new RepeatSubmit();
    private final OperationLog log = new OperationLog();
    private final Translate translate = new Translate();
    private final Dict dict = new Dict();

    public static class Dict {

        /** 是否启用内置数据库字典（classpath 有 JdbcTemplate 且未自定义 Resolver 时生效） */
        private boolean enabled = true;

        /** 字典表名 */
        private String table = "sys_dict";

        /** 字典类型列 */
        private String typeColumn = "dict_type";

        /** 字典值列 */
        private String valueColumn = "dict_value";

        /** 字典标签列 */
        private String labelColumn = "dict_label";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }

        public String getTypeColumn() {
            return typeColumn;
        }

        public void setTypeColumn(String typeColumn) {
            this.typeColumn = typeColumn;
        }

        public String getValueColumn() {
            return valueColumn;
        }

        public void setValueColumn(String valueColumn) {
            this.valueColumn = valueColumn;
        }

        public String getLabelColumn() {
            return labelColumn;
        }

        public void setLabelColumn(String labelColumn) {
            this.labelColumn = labelColumn;
        }
    }

    public static class Translate {

        /** 是否启用 @Translate 字段翻译 */
        private boolean enabled = true;

        /** 翻译结果本地缓存秒数（0 禁用） */
        private long cacheSeconds = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getCacheSeconds() {
            return cacheSeconds;
        }

        public void setCacheSeconds(long cacheSeconds) {
            this.cacheSeconds = cacheSeconds;
        }
    }

    public static class OperationLog {

        /** 是否启用 @QuickLog 操作日志切面 */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class RepeatSubmit {

        /** 是否启用 @NoRepeatSubmit 防重复提交 */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class MethodOp {

        /** 是否启用方法级注解（@QuickSave/@QuickUpdate/@QuickRemove/@QuickExport/@QuickImport） */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

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

    public MethodOp getMethodOp() {
        return methodOp;
    }

    public RepeatSubmit getRepeatSubmit() {
        return repeatSubmit;
    }

    public OperationLog getLog() {
        return log;
    }

    public Translate getTranslate() {
        return translate;
    }

    public Dict getDict() {
        return dict;
    }
}
