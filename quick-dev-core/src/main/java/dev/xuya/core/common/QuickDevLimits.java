package dev.xuya.core.common;

/**
 * 框架防御性上限（由 quick-dev.limits.* 配置装配，保护应用免受超大请求拖垮）：
 * <ul>
 *   <li>queryMaxRows：list/tree 等不分页查询的单次返回行数上限（超出报 400），默认 1_000</li>
 *   <li>exportMaxRows：单次 Excel 导出行数上限（分批拉取，达到即停止并告警），默认 100_000</li>
 *   <li>exportBatchSize：导出分批从数据库读取的批大小，默认 1_000</li>
 *   <li>importMaxRows：单次 Excel 导入行数上限（超出拒绝），默认 10_000</li>
 *   <li>inMaxSize：单字段 IN 条件值数量上限（超出报 400），默认 1_000</li>
 * </ul>
 * <p>core 中的静态工具（QueryHelper/ExcelSupport 等）无法走 Bean 注入，
 * 上限以静态值承载、由自动配置在启动时写入，属工程折衷。</p>
 */
public final class QuickDevLimits {

    private static volatile int queryMaxRows = 1_000;
    private static volatile int exportMaxRows = 100_000;
    private static volatile int exportBatchSize = 1_000;
    private static volatile int importMaxRows = 10_000;
    private static volatile int inMaxSize = 1_000;

    private QuickDevLimits() {
    }

    public static int getQueryMaxRows() {
        return queryMaxRows;
    }

    public static void setQueryMaxRows(int queryMaxRows) {
        QuickDevLimits.queryMaxRows = queryMaxRows;
    }

    public static int getExportMaxRows() {
        return exportMaxRows;
    }

    public static void setExportMaxRows(int exportMaxRows) {
        QuickDevLimits.exportMaxRows = exportMaxRows;
    }

    public static int getExportBatchSize() {
        return exportBatchSize;
    }

    public static void setExportBatchSize(int exportBatchSize) {
        QuickDevLimits.exportBatchSize = exportBatchSize;
    }

    public static int getImportMaxRows() {
        return importMaxRows;
    }

    public static void setImportMaxRows(int importMaxRows) {
        QuickDevLimits.importMaxRows = importMaxRows;
    }

    public static int getInMaxSize() {
        return inMaxSize;
    }

    public static void setInMaxSize(int inMaxSize) {
        QuickDevLimits.inMaxSize = inMaxSize;
    }
}
