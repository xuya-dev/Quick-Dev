package dev.xuya.core.excel;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.xuya.core.common.ParamException;
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
 * Excel 导入执行器：读取 -> 逐行校验 -> 事务批量插入。
 * <p>策略：任一行校验失败则整体不入库（返回 400 及前 10 行错误明细），
 * 插入阶段同一事务，失败整体回滚。返回统计 {total, inserted}。</p>
 */
public final class ExcelImportExecutor {

    private ExcelImportExecutor() {
    }

    public static Map<String, Object> execute(BaseMapper<Object> mapper, Class<?> entityClass,
                                              MultipartFile file, Validator validator,
                                              TransactionOperations transactionOperations) {
        List<Object> rows = new ArrayList<>();
        try {
            ExcelSupport.read(file, entityClass).forEach(rows::add);
        } catch (Exception e) {
            throw new ParamException("Excel 文件解析失败: " + e.getLocalizedMessage(), e);
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
