package dev.xuya.core.crud;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import dev.xuya.core.annotation.QueryField;
import dev.xuya.core.annotation.QueryType;
import dev.xuya.core.common.ParamException;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.ConversionService;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将分页/列表接口的请求参数翻译为 MyBatis-Plus QueryWrapper。
 *
 * <p>规则：</p>
 * <ul>
 *   <li>current/size 为分页参数，orderBy/order 为排序参数，不参与条件；</li>
 *   <li>与实体属性同名的参数生效，其余忽略；</li>
 *   <li>属性标注 {@link QueryField} 按注解类型拼接，未标注默认 EQ；</li>
 *   <li>参数值自动转换为字段类型（String -&gt; Long/Integer/LocalDateTime 等）；</li>
 *   <li>orderBy 必须是实体属性名（防注入），order=desc/asc。</li>
 * </ul>
 */
public final class QueryHelper {

    private static final Set<String> RESERVED = Set.of("current", "size", "orderBy", "order");

    private QueryHelper() {
    }

    public static QueryWrapper<Object> build(EntityMeta meta, Map<String, String> params,
                                             ConversionService conversionService) {
        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (RESERVED.contains(name) || value == null || value.isBlank()) {
                continue;
            }
            Field field = meta.getField(name);
            if (field == null) {
                continue; // 非实体属性，忽略
            }
            String column = meta.getColumn(name);
            QueryType type = field.isAnnotationPresent(QueryField.class)
                    ? field.getAnnotation(QueryField.class).value() : QueryType.EQ;
            switch (type) {
                case EQ -> wrapper.eq(column, convert(meta, field, value, conversionService, name));
                case NE -> wrapper.ne(column, convert(meta, field, value, conversionService, name));
                case GT -> wrapper.gt(column, convert(meta, field, value, conversionService, name));
                case GE -> wrapper.ge(column, convert(meta, field, value, conversionService, name));
                case LT -> wrapper.lt(column, convert(meta, field, value, conversionService, name));
                case LE -> wrapper.le(column, convert(meta, field, value, conversionService, name));
                case LIKE -> wrapper.like(column, value);
                case IN -> {
                    List<Object> values = new ArrayList<>();
                    for (String item : value.split(",")) {
                        String trimmed = item.trim();
                        if (!trimmed.isEmpty()) {
                            values.add(convert(meta, field, trimmed, conversionService, name));
                        }
                    }
                    if (!values.isEmpty()) {
                        wrapper.in(column, values);
                    }
                }
                default -> wrapper.eq(column, value);
            }
        }

        String orderBy = params.get("orderBy");
        if (orderBy != null && meta.hasField(orderBy)) {
            boolean desc = "desc".equalsIgnoreCase(params.get("order"));
            if (desc) {
                wrapper.orderByDesc(meta.getColumn(orderBy));
            } else {
                wrapper.orderByAsc(meta.getColumn(orderBy));
            }
        }
        return wrapper;
    }

    private static Object convert(EntityMeta meta, Field field, String value,
                                  ConversionService conversionService, String paramName) {
        try {
            return conversionService.convert(value, field.getType());
        } catch (ConversionFailedException e) {
            throw new ParamException("参数 " + paramName + " 的值 \"" + value
                    + "\" 无法转换为 " + field.getType().getSimpleName() + " 类型");
        }
    }
}
