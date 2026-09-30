package dev.xuya.springboot.autoconfigure;

import com.baomidou.mybatisplus.annotation.DbType;
import dev.xuya.core.annotation.CrudOp;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 配置项（前缀 quick-dev）。字典/日志的 JDBC 直查配置已移除：
 * 字典数据来源为 DictLoader SPI 或导入端点上传；日志落地为 OperationLogSink SPI。
 */
@ConfigurationProperties(prefix = "quick-dev")
public class QuickDevProperties {

    private final Auth auth = new Auth();
    private final AutoFill autoFill = new AutoFill();
    private final MethodOp methodOp = new MethodOp();
    private final RepeatSubmit repeatSubmit = new RepeatSubmit();
    private final OperationLog log = new OperationLog();
    private final Translate translate = new Translate();
    private final Dict dict = new Dict();
    private final Limits limits = new Limits();
    private final Crud crud = new Crud();
    /**
     * 是否启用 @QuickCrud 动态端点注册
     */
    private boolean enabled = true;
    /**
     * 未预期异常是否向客户端透出详细信息（false 返回"系统繁忙"）
     */
    private boolean errorDetail = true;
    /**
     * 分页插件方言（MyBatis-Plus DbType 名称，如 mysql / h2 / postgresql），留空自动
     */
    private DbType dbType;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isErrorDetail() {
        return errorDetail;
    }

    public void setErrorDetail(boolean errorDetail) {
        this.errorDetail = errorDetail;
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

    public Limits getLimits() {
        return limits;
    }

    public Crud getCrud() {
        return crud;
    }

    /**
     * @QuickCrud 全局默认端点集
     */
    public static class Crud {

        /**
         * 全局默认注册的操作：注解未显式指定 includes 时生效（空 = 用注解默认值）。
         * 配置示例：quick-dev.crud.default-includes: PAGE,LIST,DETAIL,SAVE,UPDATE,REMOVE
         */
        private CrudOp[] defaultIncludes = {};

        /**
         * 全局排除的操作：对所有 @QuickCrud 控制器做减法（含显式指定 includes 的控制器）。
         * 例如只想要"读+单条写"：quick-dev.crud.default-excludes: SAVE_BATCH,SAVE_OR_UPDATE
         */
        private CrudOp[] defaultExcludes = {};

        public CrudOp[] getDefaultIncludes() {
            return defaultIncludes;
        }

        public void setDefaultIncludes(CrudOp[] defaultIncludes) {
            this.defaultIncludes = defaultIncludes;
        }

        public CrudOp[] getDefaultExcludes() {
            return defaultExcludes;
        }

        public void setDefaultExcludes(CrudOp[] defaultExcludes) {
            this.defaultExcludes = defaultExcludes;
        }
    }

    public static class Auth {

        /**
         * 鉴权总开关，false 时所有鉴权注解直接放行
         */
        private boolean enabled = true;

        /**
         * 携带 token 的请求头
         */
        private String tokenHeader = "Authorization";

        /**
         * 兜底：携带 token 的请求参数名
         */
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

    public static class AutoFill {

        /**
         * 是否启用 createTime/updateTime/createBy/updateBy 自动填充
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class MethodOp {

        /**
         * 是否启用方法级注解（@QuickSave/@QuickUpdate/@QuickRemove/@QuickExport/@QuickImport）
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class RepeatSubmit {

        /**
         * 是否启用 @NoRepeatSubmit 防重复提交
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class OperationLog {

        /**
         * 是否启用 @QuickLog 操作日志切面
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Translate {

        /**
         * 是否启用 @Translate 字段翻译
         */
        private boolean enabled = true;

        /**
         * 翻译结果本地缓存秒数（0 禁用）
         */
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

    public static class Dict {

        /**
         * 是否启用内置字典缓存/翻译
         */
        private boolean enabled = true;

        /**
         * 是否注册字典刷新端点（有 DictLoader 时可用，需 dict:refresh 权限）
         */
        private boolean refreshEndpointEnabled = true;

        /**
         * 刷新端点路径
         */
        private String refreshPath = "/quick-dev/dict/refresh";

        /**
         * 定时自动刷新间隔秒数（0 禁用；依赖 DictLoader）
         */
        private long refreshIntervalSeconds = 0;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRefreshEndpointEnabled() {
            return refreshEndpointEnabled;
        }

        public void setRefreshEndpointEnabled(boolean refreshEndpointEnabled) {
            this.refreshEndpointEnabled = refreshEndpointEnabled;
        }

        public String getRefreshPath() {
            return refreshPath;
        }

        public void setRefreshPath(String refreshPath) {
            this.refreshPath = refreshPath;
        }

        public long getRefreshIntervalSeconds() {
            return refreshIntervalSeconds;
        }

        public void setRefreshIntervalSeconds(long refreshIntervalSeconds) {
            this.refreshIntervalSeconds = refreshIntervalSeconds;
        }
    }

    public static class Limits {

        /**
         * 单次 Excel 导出行数上限（超出截断并告警）
         */
        private int exportMaxRows = 100_000;

        /**
         * 单次 Excel 导入行数上限（超出拒绝）
         */
        private int importMaxRows = 10_000;

        /**
         * 单字段 IN 条件值数量上限（超出报 400）
         */
        private int inMaxSize = 1_000;

        public int getExportMaxRows() {
            return exportMaxRows;
        }

        public void setExportMaxRows(int exportMaxRows) {
            this.exportMaxRows = exportMaxRows;
        }

        public int getImportMaxRows() {
            return importMaxRows;
        }

        public void setImportMaxRows(int importMaxRows) {
            this.importMaxRows = importMaxRows;
        }

        public int getInMaxSize() {
            return inMaxSize;
        }

        public void setInMaxSize(int inMaxSize) {
            this.inMaxSize = inMaxSize;
        }
    }
}
