package dev.xuya.core.excel;

import cn.idev.excel.FastExcel;
import cn.idev.excel.event.AnalysisEventListener;
import cn.idev.excel.context.AnalysisContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.xuya.core.common.QuickDevLimits;
import jakarta.servlet.http.HttpServletResponse;
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
 */
public final class ExcelSupport {

    private static final DateTimeFormatter FILE_NAME_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private ExcelSupport() {
    }

    /**
     * 读取 Excel 原始行（含表头行，headRowNumber=0）：每行为 列索引 -> 单元格文本
     */
    @SuppressWarnings("unchecked")
    public static List<Map<Integer, String>> readRawRows(MultipartFile file) throws IOException {
        return readRawRows(file, Integer.MAX_VALUE);
    }

    /**
     * 读取原始行并在超过 maxRows 时立即中止解析（防超大文件撑爆内存）
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
                        // 含表头计数：达到上限立即停止解析，防超大文件耗尽内存
                        return rows.size() <= maxRows;
                    }

                    @Override
                    public void doAfterAllAnalysed(AnalysisContext context) {
                    }
                })
                .doRead();
        return rows;
    }

    /**
     * 导出 Excel 到 HTTP 响应（附件下载；超出 export-max-rows 截断并告警）
     */
    public static void write(HttpServletResponse response, Class<?> headClass, List<?> data) throws IOException {
        data = truncateForExport(data);
        prepareDownloadHeaders(response, headClass, "");
        FastExcel.write(response.getOutputStream(), headClass).sheet(headClass.getSimpleName()).doWrite(data);
    }

    /**
     * 导出翻译版 Excel：@Translate 字段输出为标签（经 Jackson 序列化管线，
     * 翻译与 @JsonIgnore 同时生效），列顺序/列名与普通导出保持一致。
     */
    public static void writeTranslated(HttpServletResponse response, Class<?> headClass, List<?> data,
                                       ObjectMapper objectMapper) throws IOException {
        data = truncateForExport(data);
        List<Field> fields = ExcelRowMapper.excelFields(headClass);
        List<List<String>> head = new ArrayList<>(fields.size());
        for (Field field : fields) {
            head.add(List.of(ExcelRowMapper.headNameOf(field)));
        }
        List<List<Object>> rows = new ArrayList<>(data.size());
        for (Object entity : data) {
            // convertValue 走完整序列化管线：@Translate 翻译、@JsonIgnore 排除
            @SuppressWarnings("unchecked")
            Map<String, Object> translated =
                    objectMapper.convertValue(entity, Map.class);
            List<Object> row = new ArrayList<>(fields.size());
            for (Field field : fields) {
                row.add(translated.get(field.getName()));
            }
            rows.add(row);
        }
        prepareDownloadHeaders(response, headClass, "");
        FastExcel.write(response.getOutputStream()).head(head)
                .sheet(headClass.getSimpleName()).doWrite(rows);
    }

    /**
     * 超出 export-max-rows 时截断（防御超大导出拖垮内存），并告警
     */
    private static List<?> truncateForExport(List<?> data) {
        int max = QuickDevLimits.getExportMaxRows();
        if (data != null && data.size() > max) {
            org.slf4j.LoggerFactory.getLogger(ExcelSupport.class)
                    .warn("导出行数 {} 超过上限 {}，已截断。可通过 quick-dev.limits.export-max-rows 调整",
                            data.size(), max);
            return data.subList(0, max);
        }
        return data;
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

    private static void prepareDownloadHeaders(HttpServletResponse response, Class<?> headClass, String suffix) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = headClass.getSimpleName().toLowerCase() + suffix + "-"
                + LocalDateTime.now().format(FILE_NAME_TIME);
        response.setHeader("Content-Disposition",
                "attachment;filename*=utf-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + ".xlsx");
    }
}
