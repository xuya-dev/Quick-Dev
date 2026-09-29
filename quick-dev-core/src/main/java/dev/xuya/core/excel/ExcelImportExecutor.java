package dev.xuya.core.excel;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.xuya.core.common.ParamException;
import dev.xuya.core.common.QuickDevLimits;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Excel 导入执行器：原始行读取 -> 表头映射 -> 字典标签反解（上传转换）-> 类型转换
 * -> 逐行校验 -> 事务批量插入。
 * <p>策略：任一行校验失败则整体不入库（返回 400 及前 10 行错误明细），
 * 插入阶段同一事务，失败整体回滚。返回统计 {total, inserted}。</p>
 */
public final class ExcelImportExecutor {

    private ExcelImportExecutor() {
    }

    public static Map<String, Object> execute(BaseMapper<Object> mapper, Class<?> entityClass,
                                              MultipartFile file, Validator validator,
                                              TransactionOperations transactionOperations) {
        List<Map<Integer, String>> rawRows;
        try {
            rawRows = ExcelSupport.readRawRows(file);
        } catch (Exception e) {
            throw new ParamException("Excel 文件解析失败: " + e.getLocalizedMessage(), e);
        }
        if (rawRows == null || rawRows.isEmpty()) {
            throw new ParamException("Excel 内容为空（缺少表头）");
        }
        Map<Integer, String> headRow = rawRows.remove(0);
        ExcelRowMapper rowMapper = ExcelRowMapper.of(entityClass, headRow);

        List<Object> rows = new ArrayList<>();
        for (int i = 0; i < rawRows.size(); i++) {
            rows.add(rowMapper.map(rawRows.get(i), i + 1)); // 先反解字典标签，再类型转换
        }
        int maxRows = QuickDevLimits.getImportMaxRows();
        if (rows.size() > maxRows) {
            throw new ParamException("导入行数 " + rows.size() + " 超过上限 " + maxRows
                    + "，请分批导入（可通过 quick-dev.limits.import-max-rows 调整）");
        }

        if (validator != null) {
            Map<Integer, String> errors = new LinkedHashMap<>();
            for (int i = 0; i < rows.size(); i++) {
                Set<ConstraintViolation<Object>> violations = validator.validate(rows.get(i));
                if (!violations.isEmpty()) {
                    errors.put(i + 1, violations.stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .collect(Collectors.joining("; ")));
                }
            }
            if (!errors.isEmpty()) {
                String detail = errors.entrySet().stream().limit(10)
                        .map(e -> "第" + e.getKey() + "行: " + e.getValue())
                        .collect(Collectors.joining("；"));
                throw new ParamException("导入校验失败，共 " + errors.size() + " 行有误（全部未入库）：" + detail);
            }
        }

        List<Object> finalRows = rows;
        Integer inserted;
        if (transactionOperations != null) {
            inserted = transactionOperations.execute(status -> {
                finalRows.forEach(mapper::insert);
                return finalRows.size();
            });
        } else {
            finalRows.forEach(mapper::insert);
            inserted = finalRows.size();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", finalRows.size());
        result.put("inserted", inserted);
        return result;
    }
}
