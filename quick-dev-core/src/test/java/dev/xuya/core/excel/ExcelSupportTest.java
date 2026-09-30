package dev.xuya.core.excel;

import cn.idev.excel.FastExcel;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.xuya.core.common.QuickDevLimits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 导出分批取数：读满 export-max-rows 即停（数据库侧只取所需页），
 * 与行数边界（恰好等于上限、跨批、空结果）。
 */
class ExcelSupportTest {

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void restoreLimits() throws Exception {
        QuickDevLimits.setExportMaxRows(100_000);
        QuickDevLimits.setExportBatchSize(1_000);
    }

    /**
     * 生成 rows 行批数据的取数器：每批 batchSize 行
     */
    private ExcelSupport.BatchFetcher fetcher(int totalRows, int batchSize) {
        return batchIndex -> {
            int from = batchIndex * batchSize;
            if (from >= totalRows) {
                return List.of();
            }
            List<Object> batch = new ArrayList<>();
            for (int i = from; i < Math.min(from + batchSize, totalRows); i++) {
                final int id = i + 1;
                batch.add(new SampleRow("row-" + id, id));
            }
            return batch;
        };
    }

    private List<Map<Integer, String>> readBack() {
        return FastExcel.read(new ByteArrayInputStream(response.getContentAsByteArray()))
                .sheet().headRowNumber(0).doReadSync();
    }

    @Test
    void allRowsShouldBeWrittenAcrossBatches() throws Exception {
        QuickDevLimits.setExportBatchSize(3);
        ExcelSupport.write(response, SampleRow.class, fetcher(7, 3));

        // 表头 1 行 + 数据 7 行（跨 3 个批次）
        List<Map<Integer, String>> rows = readBack();
        assertThat(rows).hasSize(8);
        assertThat(rows.get(1).get(1)).isEqualTo("1");
        assertThat(rows.get(7).get(0)).isEqualTo("row-7");
    }

    @Test
    void exportShouldStopAtMaxRows() throws Exception {
        QuickDevLimits.setExportMaxRows(4);
        QuickDevLimits.setExportBatchSize(3);
        ExcelSupport.write(response, SampleRow.class, fetcher(100, 3));

        // 表头 1 行 + 数据 4 行：分批拉取在达到上限时停止，而不是把 100 行全查回来
        assertThat(readBack()).hasSize(5);
    }

    @Test
    void exactMaxRowsShouldNotTruncate() throws Exception {
        QuickDevLimits.setExportMaxRows(5);
        QuickDevLimits.setExportBatchSize(5);
        ExcelSupport.write(response, SampleRow.class, fetcher(5, 5));

        // 恰好等于上限：全部写出，不告警截断
        assertThat(readBack()).hasSize(6);
    }

    @Test
    void emptyFetcherShouldProduceHeaderOnlyFile() throws Exception {
        ExcelSupport.write(response, SampleRow.class, fetcher(0, 10));
        List<Map<Integer, String>> rows = readBack();
        assertThat(rows).hasSize(1); // 只有表头
        assertThat(rows.get(0).get(0)).isEqualTo("名称");
    }

    @Test
    void writeTranslatedShouldEmitJsonPropertyAlignedHead() throws Exception {
        QuickDevLimits.setExportBatchSize(2);
        ExcelSupport.writeTranslated(response, SampleRow.class, fetcher(3, 2), new ObjectMapper());

        List<Map<Integer, String>> rows = readBack();
        assertThat(rows).hasSize(4); // 表头 + 3 行
        assertThat(rows.get(0).get(0)).isEqualTo("名称");
        assertThat(rows.get(0).get(1)).isEqualTo("score");
    }

    @Test
    void importTemplateShouldContainHeaderOnly() throws Exception {
        ExcelSupport.writeTemplate(response, SampleRow.class);
        assertThat(readBack()).hasSize(1);
    }

    public static class SampleRow {

        @cn.idev.excel.annotation.ExcelProperty("名称")
        private final String name;

        private final Integer score;

        public SampleRow(String name, Integer score) {
            this.name = name;
            this.score = score;
        }

        public String getName() {
            return name;
        }

        public Integer getScore() {
            return score;
        }
    }
}
