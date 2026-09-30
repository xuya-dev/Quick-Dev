package dev.xuya.core.crud;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import dev.xuya.core.common.QuickDevException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实体元信息：主键字段、属性与列名的映射。
 * <p>优先读取 MyBatis-Plus 的 {@link TableInfo}（Mapper 已初始化时），
 * 找不到时退化为反射 + 驼峰转下划线，保证在任意阶段都能构建。</p>
 */
public class EntityMeta {

    /**
     * 实体元信息缓存：EntityMeta 在翻译序列化、方法级注解 AOP 等热路径上
     * 每请求都会取用，而构建需要全字段反射扫描 + setAccessible，开销不可忽略。
     * 元信息在运行期不可变，进程级缓存是安全的。
     */
    private static final java.util.concurrent.ConcurrentHashMap<Class<?>, EntityMeta> CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    private final Class<?> entityClass;
    private final Field idField;
    private final String idProperty;
    private final String idColumn;
    private final String logicDeleteProperty;
    private final Map<String, Field> fieldMap = new LinkedHashMap<>();
    private final Map<String, String> columnMap = new LinkedHashMap<>();

    private EntityMeta(Class<?> entityClass, Field idField, String idColumn,
                       String logicDeleteProperty,
                       Map<String, Field> fieldMap, Map<String, String> columnMap) {
        this.entityClass = entityClass;
        this.idField = idField;
        this.idProperty = idField.getName();
        this.idColumn = idColumn;
        this.logicDeleteProperty = logicDeleteProperty;
        this.fieldMap.putAll(fieldMap);
        this.columnMap.putAll(columnMap);
    }

    public static EntityMeta of(Class<?> entityClass) {
        EntityMeta cached = CACHE.get(entityClass);
        if (cached != null) {
            return cached;
        }
        return CACHE.computeIfAbsent(entityClass, EntityMeta::build);
    }

    private static EntityMeta build(Class<?> entityClass) {
        Map<String, Field> fields = new LinkedHashMap<>();
        for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                    continue;
                }
                field.setAccessible(true);
                fields.putIfAbsent(field.getName(), field);
            }
        }

        Field idField = fields.values().stream()
                .filter(f -> f.isAnnotationPresent(TableId.class))
                .findFirst()
                .orElseGet(() -> fields.get("id"));
        if (idField == null) {
            throw new QuickDevException("实体 " + entityClass.getName() + " 未找到主键字段（@TableId 或 id）");
        }

        Map<String, String> columns = new LinkedHashMap<>();
        String logicDeleteProperty = null;
        TableInfo tableInfo = TableInfoHelper.getTableInfo(entityClass);
        if (tableInfo != null) {
            for (TableFieldInfo fieldInfo : tableInfo.getFieldList()) {
                columns.put(fieldInfo.getProperty(), fieldInfo.getColumn());
                if (fieldInfo.isLogicDelete()) {
                    logicDeleteProperty = fieldInfo.getProperty();
                }
            }
        }
        for (String property : fields.keySet()) {
            if (isNonColumnField(fields.get(property))) {
                continue; // @TableField(exist = false)：非表字段（如树形 children），不参与查询条件
            }
            columns.putIfAbsent(property, camelToSnake(property));
        }
        String idColumn = tableInfo != null && tableInfo.getKeyColumn() != null
                ? tableInfo.getKeyColumn() : camelToSnake(idField.getName());
        columns.put(idField.getName(), idColumn);

        return new EntityMeta(entityClass, idField, idColumn, logicDeleteProperty, fields, columns);
    }

    /**
     * @TableField(exist = false) 标注的非表字段
     */
    private static boolean isNonColumnField(Field field) {
        TableField tableField = field.getAnnotation(TableField.class);
        return tableField != null && !tableField.exist();
    }

    /**
     * 驼峰转下划线
     */
    public static String camelToSnake(String name) {
        StringBuilder sb = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public Class<?> getEntityClass() {
        return entityClass;
    }

    public Field getIdField() {
        return idField;
    }

    public String getIdProperty() {
        return idProperty;
    }

    public String getIdColumn() {
        return idColumn;
    }

    public Class<?> getIdType() {
        return idField.getType();
    }

    public Field getField(String property) {
        return fieldMap.get(property);
    }

    public boolean hasField(String property) {
        return fieldMap.containsKey(property);
    }

    public String getColumn(String property) {
        return columnMap.get(property);
    }

    public Map<String, String> getColumnMap() {
        return columnMap;
    }

    /**
     * 逻辑删除字段属性名（无逻辑删除时为 null）
     */
    public String getLogicDeleteProperty() {
        return logicDeleteProperty;
    }

    /**
     * 写路径保护：清空客户端不应操纵的系统字段——
     * 逻辑删除字段（防 PUT {"delFlag":2} 越权删除/复活记录）与
     * createBy/updateBy（防伪造审计归属，清空后由 AutoFill 按登录人填充）。
     */
    public void clearSystemFields(Object entity) {
        if (entity == null) {
            return;
        }
        clearField(entity, logicDeleteProperty);
        clearField(entity, AutoFillMetaObjectHandler.CREATE_BY);
        clearField(entity, AutoFillMetaObjectHandler.UPDATE_BY);
    }

    private void clearField(Object entity, String property) {
        if (property == null) {
            return;
        }
        Field field = fieldMap.get(property);
        if (field == null) {
            return;
        }
        try {
            field.set(entity, null);
        } catch (IllegalAccessException e) {
            throw new QuickDevException("无法清空保护字段 " + property + ": " + e.getMessage(), e);
        }
    }
}
