package dev.xuya.core.excel;

import cn.idev.excel.annotation.ExcelIgnore;
import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableField;
import dev.xuya.core.common.ParamException;
import dev.xuya.core.translate.Translate;
import dev.xuya.core.translate.TranslateExecutor;
import org.springframework.core.convert.ConversionService;
import org.springframework.format.support.DefaultFormattingConversionService;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel 行映射器：按表头文本映射实体字段（@ExcelProperty 值或字段名），
 * 每个单元格先做 @Translate 反解（标签 -> 值，"上传转换"），再做类型转换。
 *
 * <p>为什么要绕开 FastExcel 的实体直读：用户在字典/状态列填的是中文标签，
 * 直接读 Integer 字段会在转换阶段报错；先反解为原始值再转换才能成功。</p>
 */
public final class ExcelRowMapper {

    private final Map<Integer, Field> columns = new LinkedHashMap<>();
    private final ConversionService conversionService = new DefaultFormattingConversionService();

    private ExcelRowMapper() {
    }

    /**
     * 按表头行构建列映射：单元格文本 == @ExcelProperty 值或字段名（去首尾空格）
     */
    public static ExcelRowMapper of(Class<?> entityClass, Map<Integer, String> headRow) {
        ExcelRowMapper mapper = new ExcelRowMapper();
        for (Field field : excelFields(entityClass)) {
            for (Map.Entry<Integer, String> head : headRow.entrySet()) {
                String cell = head.getValue() == null ? "" : head.getValue().trim();
                if (cell.isEmpty()) {
                    continue;
                }
                if (cell.equals(headName(field)) || cell.equals(field.getName())) {
                    mapper.columns.putIfAbsent(head.getKey(), field);
                    break;
                }
            }
        }
        return mapper;
    }

    /**
     * 参与导入导出的实体字段（排除静态/瞬态/@ExcelIgnore/@TableField(exist=false)）
     */
    static List<Field> excelFields(Class<?> entityClass) {
        List<Field> result = new ArrayList<>();
        for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)
                        || field.isAnnotationPresent(ExcelIgnore.class)) {
                    continue;
                }
                TableField tableField = field.getAnnotation(TableField.class);
                if (tableField != null && !tableField.exist()) {
                    continue;
                }
                field.setAccessible(true);
                result.add(field);
            }
        }
        return result;
    }

    /**
     * Excel 列头名：@ExcelProperty 值，未标注用字段名（导出/导入两侧保持一致）
     */
    static String headNameOf(Field field) {
        ExcelProperty property = field.getAnnotation(ExcelProperty.class);
        return property != null && property.value().length > 0 && !property.value()[0].isEmpty()
                ? property.value()[0] : field.getName();
    }

    private static String headName(Field field) {
        return headNameOf(field);
    }

    /**
     * 一行原始数据 -> 实体（rowNumber 从 1 开始计数据行，用于报错定位）
     */
    public Object map(Map<Integer, String> row, int rowNumber) {
        Object entity;
        try {
            entity = columns.isEmpty()
                    ? null
                    : columns.values().iterator().next().getDeclaringClass().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new ParamException("实体需要无参构造函数: " + e.getMessage(), e);
        }
        if (entity == null) {
            return null;
        }
        for (Map.Entry<Integer, Field> entry : columns.entrySet()) {
            String cell = row.get(entry.getKey());
            if (cell == null || cell.isBlank()) {
                continue;
            }
            Field field = entry.getValue();
            Object value = cell.trim();
            Translate translate = field.getAnnotation(Translate.class);
            if (translate != null) {
                TranslateExecutor executor = TranslateExecutor.getInstance();
                if (executor != null) {
                    Object reversed = executor.reverse(translate, String.valueOf(value));
                    if (reversed != null) {
                        value = reversed; // 标签 -> 值（上传转换）
                    }
                }
            }
            if (value instanceof String text && field.getType() != String.class) {
                try {
                    value = conversionService.convert(text, field.getType());
                } catch (Exception e) {
                    throw new ParamException("第 " + rowNumber + " 行 [" + headName(field) + "] 的值 \""
                            + text + "\" 无法转换为 " + field.getType().getSimpleName());
                }
            }
            try {
                field.set(entity, value);
            } catch (IllegalAccessException e) {
                throw new ParamException("第 " + rowNumber + " 行写入字段 " + field.getName() + " 失败", e);
            }
        }
        return entity;
    }
}
