package dev.xuya.core.excel;

import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.event.AnalysisEventListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.xuya.core.common.QuickDevLimits;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * FastExcel 读写封装（列名映射：实体字段加 @ExcelProperty("中文名")，未加则按字段名导出）。
 *
 * <p>导出不再"一次性把全量结果查进堆"：数据由 {@link BatchFetcher} 按
 * {@link QuickDevLimits#getExportBatchSize()} 分批提供，读满
 * {@link QuickDevLimits#getExportMaxRows()} 立即停止拉取并告警截断——
 * 命中千万行时数据库侧只取所需页，而不是把整表物化后再裁掉。</p>
 *
 * <p>峰值堆占用上界 ≈ export-max-rows（默认 10 万行）；若该值配置得很大，
 * 请同时调大 export-batch-size 以摊薄分批开销，或直接调小上限。</p>
 */
public final class ExcelSupport {

    private static final Logger log = LoggerFactory.getLogger(ExcelSupport.class);

    private static final DateTimeFormatter FILE_NAME_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private ExcelSupport() {
    }

    /**
     * 单批数据来源：返回该批数据；返回 null 或空集合表示没有更多数据
     */
    @FunctionalInterface
    public interface BatchFetcher {
        /**
         * @param batchIndex 批序号（从 0 开始）
         * @return 该批数据；null/空表示取完
         */
        List<?> fetch(int batchIndex);
    }

    // ------------------------------------------------------------------
    // 读
    // ------------------------------------------------------------------

    /**
     * 读取 Excel 原始行（含表头行，headRowNumber=0）：每行为 列索引 -> 单元格文本
     */
    public static List<Map<Integer, String>> readRawRows(MultipartFile file) throws IOException {
        return readRawRows(file, Integer.MAX_VALUE);
    }

    /**
     * 读取原始行并在读满 maxRows 时立即停止解析（防超大文件撑爆内存）
     *
     * @param maxRows 最多保留的行数（含表头行）
     */
    public static List<Map<Integer, String>> readRawRows(MultipartFile file, int maxRows) throws IOException {
        List<Map<Integer, String>> rows = new ArrayList<>();
        FastExcel.read(file.getInputStream())
                .sheet()
                .headRowNumber(0)
                .registerReadListener(new AnalysisEventListener<Map<Integer, String>>() {
                    @Override
                    public void invoke(Map<Integer, String> row, AnalysisContext context) {
                        rows.add(row);
                    }

                    @Override
                    public boolean hasNext(AnalysisContext context) {
                        // 严格小于：读满 maxRows 即停，避免多读一行导致"恰好等于上限"的文件被误判超限
                        return rows.size() < maxRows;
                    }

                    @Override
                    public void doAfterAllAnalysed(AnalysisContext context) {
                    }
                })
                .doRead();
        return rows;
    }

    // ------------------------------------------------------------------
    // 写
    // ------------------------------------------------------------------

    /**
     * 导出 Excel 到 HTTP 响应（按实体 @ExcelProperty 出列）
     */
    public static void write(HttpServletResponse response, Class<?> headClass, BatchFetcher fetcher)
            throws IOException {
        List<Object> data = fetchUpTo(fetcher);
        prepareDownloadHeaders(response, headClass, "");
        FastExcel.write(response.getOutputStream(), headClass).sheet(headClass.getSimpleName()).doWrite(data);
    }

    /**
     * 导出翻译版 Excel：@Translate 字段输出为标签（经 Jackson 序列化管线，
     * 翻译与 @JsonIgnore 同时生效），列顺序/列名与普通导出保持一致。
     */
    public static void writeTranslated(HttpServletResponse response, Class<?> headClass, BatchFetcher fetcher,
                                       ObjectMapper objectMapper) throws IOException {
        List<Object> data = fetchUpTo(fetcher);
        List<Field> fields = ExcelRowMapper.excelFields(headClass);

        List<List<Object>> rows = new ArrayList<>(data.size());
        for (Object entity : data) {
            // convertValue 走完整序列化管线：@Translate 翻译、@JsonIgnore 排除
            @SuppressWarnings("unchecked")
            Map<String, Object> translated = objectMapper.convertValue(entity, Map.class);
            List<Object> row = new ArrayList<>(fields.size());
            for (Field field : fields) {
                // 按 JSON 属性名取值：字段被 @JsonProperty 改名时同样取得到
                row.add(translated.get(ExcelRowMapper.jsonNameOf(field)));
            }
            rows.add(row);
        }

        List<List<String>> head = new ArrayList<>(fields.size());
        for (Field field : fields) {
            head.add(List.of(ExcelRowMapper.headNameOf(field)));
        }
        prepareDownloadHeaders(response, headClass, "");
        FastExcel.write(response.getOutputStream()).head(head).sheet(headClass.getSimpleName()).doWrite(rows);
    }

    /**
     * 生成导入模板：只有表头、没有数据的 Excel
     */
    public static void writeTemplate(HttpServletResponse response, Class<?> headClass) throws IOException {
        prepareDownloadHeaders(response, headClass, "-template");
        FastExcel.write(response.getOutputStream(), headClass)
                .sheet(headClass.getSimpleName())
                .doWrite(List.of());
    }

    /**
     * 分批拉取数据，累计到 export-max-rows 即停止（并在确实被截断时告警）。
     */
    private static List<Object> fetchUpTo(BatchFetcher fetcher) {
        int maxRows = Math.max(0, QuickDevLimits.getExportMaxRows());
        int batchSize = Math.max(1, QuickDevLimits.getExportBatchSize());
        List<Object> data = new ArrayList<>(Math.min(maxRows, batchSize));
        boolean truncated = false;
        for (int batchIndex = 0; !truncated; batchIndex++) {
            List<?> batch = fetcher.fetch(batchIndex);
            if (batch == null || batch.isEmpty()) {
                break;
            }
            for (Object row : batch) {
                if (data.size() >= maxRows) {
                    truncated = true;
                    break;
                }
                data.add(row);
            }
        }
        if (truncated) {
            log.warn("导出行数达到上限 {}，已截断（后续数据未导出）。可通过 quick-dev.limits.export-max-rows 调整",
                    maxRows);
        }
        return data;
    }

    private static void prepareDownloadHeaders(HttpServletResponse response, Class<?> headClass, String suffix) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = headClass.getSimpleName().toLowerCase() + suffix + "-"
                + LocalDateTime.now().format(FILE_NAME_TIME);
        response.setHeader("Content-Disposition",
                "attachment;filename*=utf-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + ".xlsx");
    }
}
