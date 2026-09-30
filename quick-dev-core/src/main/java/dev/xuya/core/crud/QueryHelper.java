package dev.xuya.core.crud;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import dev.xuya.core.annotation.QueryField;
import dev.xuya.core.annotation.QueryType;
import dev.xuya.core.auth.AuthContext;
import dev.xuya.core.common.ParamException;
import dev.xuya.core.common.QuickDevLimits;
import dev.xuya.core.context.SpringContextHolder;
import dev.xuya.core.datascope.DataScope;
import dev.xuya.core.datascope.DataScopeResolver;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.ConversionService;

import java.lang.reflect.Field;
import java.util.*;

/**
 * 将分页/列表接口的请求参数翻译为 MyBatis-Plus QueryWrapper。
 *
 * <p>规则：</p>
 * <ul>
 *   <li>current/size 为分页参数，orderBy/order 为排序参数，不参与条件；</li>
 *   <li>与实体属性同名的参数生效，其余忽略；</li>
 *   <li>属性标注 {@link QueryField} 按注解类型拼接，未标注默认 EQ；</li>
 *   <li>参数值自动转换为字段类型（String -&gt; Long/Integer/LocalDateTime 等）；</li>
 *   <li>orderBy 必须是实体属性名（防注入），order=desc/asc；</li>
 *   <li>实体标注 {@link DataScope} 且注册了 {@link DataScopeResolver} 时，
 *       追加行级数据权限过滤条件。</li>
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
            if (column == null) {
                continue; // 非表字段（如 @TableField(exist=false) 的 children），不参与条件
            }
            QueryType type = field.isAnnotationPresent(QueryField.class)
                    ? field.getAnnotation(QueryField.class).value() : QueryType.EQ;
            switch (type) {
                case EQ -> wrapper.eq(column, convert(meta, field, value, conversionService, name));
                case NE -> wrapper.ne(column, convert(meta, field, value, conversionService, name));
                case GT -> wrapper.gt(column, convert(meta, field, value, conversionService, name));
                case GE -> wrapper.ge(column, convert(meta, field, value, conversionService, name));
                case LT -> wrapper.lt(column, convert(meta, field, value, conversionService, name));
                case LE -> wrapper.le(column, convert(meta, field, value, conversionService, name));
                case LIKE -> {
                    QueryField queryField = field.getAnnotation(QueryField.class);
                    wrapper.like(column, queryField != null && queryField.escapeWildcard()
                            ? escapeWildcard(value) : value);
                }
                case IN -> {
                    List<Object> values = new ArrayList<>();
                    for (String item : value.split(",")) {
                        String trimmed = item.trim();
                        if (!trimmed.isEmpty()) {
                            values.add(convert(meta, field, trimmed, conversionService, name));
                        }
                    }
                    if (values.size() > QuickDevLimits.getInMaxSize()) {
                        throw new ParamException("参数 " + name + " 的 IN 值数量 " + values.size()
                                + " 超过上限 " + QuickDevLimits.getInMaxSize()
                                + "（可通过 quick-dev.limits.in-max-size 调整）");
                    }
                    if (!values.isEmpty()) {
                        wrapper.in(column, values);
                    }
                }
                case BETWEEN -> {
                    String[] range = value.split(",", -1);
                    if (range.length != 2 || range[0].isBlank() || range[1].isBlank()) {
                        throw new ParamException("参数 " + name + " 的 BETWEEN 值必须为 \"起始值,结束值\"");
                    }
                    wrapper.between(column,
                            convert(meta, field, range[0].trim(), conversionService, name),
                            convert(meta, field, range[1].trim(), conversionService, name));
                }
                default -> wrapper.eq(column, value);
            }
        }

        appendOrderBy(meta, params, wrapper);
        applyDataScope(meta, wrapper);
        return wrapper;
    }

    /**
     * 排序：orderBy 支持逗号分隔的多列（如 createTime,id），order 逐列对应
     * （缺省 asc；仅一个 order 值时对所有列生效）。列必须是实体表字段（白名单防注入）。
     */
    private static void appendOrderBy(EntityMeta meta, Map<String, String> params,
                                      QueryWrapper<Object> wrapper) {
        String orderBy = params.get("orderBy");
        if (orderBy == null || orderBy.isBlank()) {
            return;
        }
        String[] orderValues = params.getOrDefault("order", "").toLowerCase().split(",");
        int index = 0;
        for (String property : orderBy.split(",")) {
            String name = property.trim();
            if (name.isEmpty()) {
                continue;
            }
            String column = meta.hasField(name) ? meta.getColumn(name) : null;
            if (column == null) {
                continue; // 非表字段（如树形 children）不参与排序
            }
            boolean desc = index < orderValues.length
                    ? "desc".equals(orderValues[index].trim()) : false;
            if (desc) {
                wrapper.orderByDesc(column);
            } else {
                wrapper.orderByAsc(column);
            }
            index++;
        }
    }

    /**
     * LIKE 通配符转义：\ 为转义符（MySQL/H2 默认即支持），用户输入按字面量匹配
     */
    private static String escapeWildcard(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (char c : value.toCharArray()) {
            if (c == '%' || c == '_' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * 行级数据权限：实体标注 @DataScope 且注册 DataScopeResolver 时追加可见范围条件
     */
    private static void applyDataScope(EntityMeta meta, QueryWrapper<Object> wrapper) {
        DataScope dataScope = meta.getEntityClass().getAnnotation(DataScope.class);
        if (dataScope == null) {
            return;
        }
        DataScopeResolver resolver = SpringContextHolder.getBeanIfAvailable(DataScopeResolver.class);
        if (resolver == null) {
            return; // 未实现数据权限 SPI：不过滤
        }
        Collection<?> scope = resolver.visibleScope(
                meta.getEntityClass(), dataScope.column(), AuthContext.getUser());
        if (scope != null) {
            if (scope.isEmpty()) {
                // 空集合 = 全不可见。不能拼 IN ()（MySQL 语法错误），用恒假条件表达"一行都看不到"
                wrapper.apply("1 = 0");
            } else {
                wrapper.in(dataScope.column(), scope);
            }
        }
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
