package dev.xuya.core.crud;

import dev.xuya.core.common.QuickDevException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 树形构建：按 parentId 分组挂 children，返回根节点列表。
 *
 * <p>实体约定：包含 {@code parentId} 字段（父 ID，null 或 0 视为根）和
 * {@code children} 字段（List 类型，需标注 {@code @TableField(exist = false)}）。</p>
 */
public final class TreeBuilder {

    private TreeBuilder() {
    }

    public static List<Object> build(EntityMeta meta, List<Object> nodes) {
        Field parentField = meta.getField("parentId");
        Field childrenField = meta.getField("children");
        if (parentField == null || childrenField == null) {
            throw new QuickDevException("实体 " + meta.getEntityClass().getSimpleName()
                    + " 使用树形接口需要同时声明 parentId 字段与 children 字段"
                    + "（children 需标注 @TableField(exist = false)）");
        }

        List<Object> roots = new ArrayList<>();
        // parentId -> 子节点列表（保留库返回顺序）
        Map<String, List<Object>> byParent = new LinkedHashMap<>();
        for (Object node : nodes) {
            byParent.computeIfAbsent(parentKey(meta, parentField, node), k -> new ArrayList<>()).add(node);
        }
        for (Object node : nodes) {
            Object id = idValue(meta, node);
            List<Object> children = id == null ? null : byParent.get(String.valueOf(id));
            try {
                childrenField.set(node, children == null ? new ArrayList<>() : children);
            } catch (IllegalAccessException e) {
                throw new QuickDevException("无法写入 children 字段: " + e.getMessage(), e);
            }
            if (isRoot(meta, parentField, node)) {
                roots.add(node);
            }
        }
        return roots;
    }

    private static String parentKey(EntityMeta meta, Field parentField, Object node) {
        Object parentId = value(parentField, node);
        return parentId == null ? "null" : String.valueOf(parentId);
    }

    private static boolean isRoot(EntityMeta meta, Field parentField, Object node) {
        Object parentId = value(parentField, node);
        return parentId == null || "0".equals(String.valueOf(parentId));
    }

    private static Object idValue(EntityMeta meta, Object node) {
        try {
            return meta.getIdField().get(node);
        } catch (IllegalAccessException e) {
            throw new QuickDevException("无法读取主键: " + e.getMessage(), e);
        }
    }

    private static Object value(Field field, Object node) {
        try {
            return field.get(node);
        } catch (IllegalAccessException e) {
            throw new QuickDevException("无法读取字段 " + field.getName() + ": " + e.getMessage(), e);
        }
    }
}
