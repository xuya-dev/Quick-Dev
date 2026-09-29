package dev.xuya.core.excel;

import cn.idev.excel.FastExcel;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * FastExcel 读写封装（列名映射：实体字段加 @ExcelProperty("中文名")，未加则按字段名导出）。
 */
public final class ExcelSupport {

    private static final DateTimeFormatter FILE_NAME_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private ExcelSupport() {
    }

    /** 读取 Excel（第一个 sheet，按表头映射到实体） */
    public static List<?> read(MultipartFile file, Class<?> headClass) throws IOException {
        return FastExcel.read(file.getInputStream()).head(headClass).sheet().doReadSync();
    }

    /** 导出 Excel 到 HTTP 响应（附件下载） */
    public static void write(HttpServletResponse response, Class<?> headClass, List<?> data) throws IOException {
        prepareDownloadHeaders(response, headClass, "");
        FastExcel.write(response.getOutputStream(), headClass).sheet(headClass.getSimpleName()).doWrite(data);
    }

    /** 生成导入模板：只有表头、没有数据的 Excel */
    public static void writeTemplate(HttpServletResponse response, Class<?> headClass) throws IOException {
        prepareDownloadHeaders(response, headClass, "-template");
        FastExcel.write(response.getOutputStream(), headClass)
                .sheet(headClass.getSimpleName())
                .doWrite(java.util.List.of());
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
