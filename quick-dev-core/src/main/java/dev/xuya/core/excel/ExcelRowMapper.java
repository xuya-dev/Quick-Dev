package dev.xuya.core.excel;

import cn.idev.excel.annotation.ExcelIgnore;
import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.xuya.core.common.ParamException;
import dev.xuya.core.translate.Translate;
import dev.xuya.core.translate.TranslateExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.ConversionService;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.util.ReflectionUtils;

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
 *
 * <p>表头校验：一个都匹配不上直接报 400（否则会静默插入一堆空记录），
 * 部分列匹配不上则告警并列出未识别的表头，方便定位列名写错。</p>
 */
public final class ExcelRowMapper {

    private static final Logger log = LoggerFactory.getLogger(ExcelRowMapper.class);

    private final Class<?> entityClass;
    private final Map<Integer, Field> columns = new LinkedHashMap<>();
    private final ConversionService conversionService = new DefaultFormattingConversionService();

    private ExcelRowMapper(Class<?> entityClass) {
        this.entityClass = entityClass;
    }

    /**
     * 按表头行构建列映射：单元格文本 == @ExcelProperty 值或字段名（去首尾空格）
     *
     * @throws ParamException 表头一个字段都匹配不上（列名与实体完全不符）
     */
    public static ExcelRowMapper of(Class<?> entityClass, Map<Integer, String> headRow) {
        ExcelRowMapper mapper = new ExcelRowMapper(entityClass);
        List<String> unmatched = new ArrayList<>();
        for (Map.Entry<Integer, String> head : headRow.entrySet()) {
            String cell = head.getValue() == null ? "" : head.getValue().trim();
            if (cell.isEmpty() || isFullyMatched(mapper.columns, cell)) {
                continue;
            }
            Field matched = matchField(entityClass, cell);
            if (matched != null && !mapper.columns.containsValue(matched)) {
                mapper.columns.put(head.getKey(), matched);
            } else {
                unmatched.add(cell);
            }
        }
        if (mapper.columns.isEmpty()) {
            throw new ParamException("Excel 表头未匹配到 " + entityClass.getSimpleName()
                    + " 的任何字段，请检查列名是否与 @ExcelProperty 或字段名一致。当前表头: " + headRow.values());
        }
        if (!unmatched.isEmpty()) {
            log.warn("Excel 导入 [{}] 有 {} 列表头未识别，将被忽略: {}",
                    entityClass.getSimpleName(), unmatched.size(), unmatched);
        }
        return mapper;
    }

    private static boolean isFullyMatched(Map<Integer, Field> columns, String cell) {
        return columns.values().stream().anyMatch(f -> matches(f, cell));
    }

    private static Field matchField(Class<?> entityClass, String cell) {
        for (Field field : excelFields(entityClass)) {
            if (matches(field, cell)) {
                return field;
            }
        }
        return null;
    }

    private static boolean matches(Field field, String cell) {
        return cell.equals(headNameOf(field)) || cell.equals(field.getName());
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

    /**
     * 该字段在 JSON 序列化结果中的属性名。
     * <p>翻译导出按 JSON 结果取值（@Translate 生效），而 Jackson 的键受
     * {@code @JsonProperty} 影响，因此不能直接用 {@link Field#getName()}。</p>
     */
    static String jsonNameOf(Field field) {
        JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
        return jsonProperty != null && !jsonProperty.value().isEmpty()
                ? jsonProperty.value() : field.getName();
    }

    /**
     * 一行原始数据 -> 实体（rowNumber 从 1 开始计数据行，用于报错定位）
     */
    public Object map(Map<Integer, String> row, int rowNumber) {
        Object entity;
        try {
            entity = ReflectionUtils.accessibleConstructor(entityClass).newInstance();
        } catch (ReflectiveOperationException e) {
            throw new ParamException("实体 " + entityClass.getSimpleName() + " 需要可访问的无参构造函数: "
                    + e.getMessage(), e);
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
                    String reversed = executor.reverse(translate, String.valueOf(value));
                    if (reversed != null) {
                        value = reversed; // 标签 -> 值（上传转换）
                    }
                }
            }
            // 反解结果统一为 String，按目标字段类型转换（含已经是字符串但类型不同的情况）
            if (!field.getType().isInstance(value)) {
                try {
                    value = conversionService.convert(String.valueOf(value), field.getType());
                } catch (Exception e) {
                    throw new ParamException("第 " + rowNumber + " 行 [" + headNameOf(field) + "] 的值 \""
                            + value + "\" 无法转换为 " + field.getType().getSimpleName());
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
