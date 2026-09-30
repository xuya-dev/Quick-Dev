package dev.xuya.core.crud;

import dev.xuya.core.common.QuickDevException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 树形构建：按 parentId 分组挂 children，返回根节点列表。
 *
 * <p>实体约定：包含 {@code parentId} 字段（父 ID，null 或 0 视为根）和
 * {@code children} 字段（List 类型，需标注 {@code @TableField(exist = false)}）。</p>
 *
 * <p>防御策略：</p>
 * <ul>
 *   <li><b>环检测</b>：数据中出现 {@code parentId} 环（含自引用 {@code id == parentId}）时，
 *       直接报错而不是构建出循环对象图——否则 Jackson 序列化会无限递归直至 StackOverflowError；</li>
 *   <li><b>孤儿节点</b>：父节点不在本次结果集内（被查询条件过滤、父行已删除）时按根节点返回，
 *       避免节点从结果里静默消失。</li>
 * </ul>
 */
public final class TreeBuilder {

    /** parentId 为 null 时使用的分组键（表示"根"） */
    private static final String ROOT_KEY = "null";

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

        Map<String, List<Object>> byParent = new LinkedHashMap<>();
        Map<String, String> parentOf = new LinkedHashMap<>();
        for (Object node : nodes) {
            String parentKey = parentKey(meta, parentField, node);
            byParent.computeIfAbsent(parentKey, k -> new ArrayList<>()).add(node);
            Object id = idValue(meta, node);
            if (id != null) {
                parentOf.put(String.valueOf(id), parentKey);
            }
        }

        List<Object> roots = new ArrayList<>();
        for (Object node : nodes) {
            Object id = idValue(meta, node);
            String idKey = id == null ? null : String.valueOf(id);
            String parentKey = parentKey(meta, parentField, node);
            if (idKey != null && idKey.equals(parentKey)) {
                throw new QuickDevException("实体 " + meta.getEntityClass().getSimpleName()
                        + " 存在自引用节点（id = parentId = " + idKey
                        + "），请修正数据；否则 JSON 序列化会无限递归");
            }
            List<Object> children = idKey == null ? null : byParent.get(idKey);
            try {
                childrenField.set(node, children == null ? new ArrayList<>() : children);
            } catch (IllegalAccessException e) {
                throw new QuickDevException("无法写入 children 字段: " + e.getMessage(), e);
            }
            if (isRoot(meta, parentField, node) || isDanglingParent(parentKey, parentOf)) {
                roots.add(node);
            }
        }
        detectCycles(meta, parentOf);
        return roots;
    }

    /**
     * 悬挂父引用：声明了非根 parentId，但该父节点不在本次结果集内
     * （被查询条件过滤、父行已删除）。这类节点按根返回，避免静默消失。
     */
    private static boolean isDanglingParent(String parentKey, Map<String, String> parentOf) {
        return !ROOT_KEY.equals(parentKey) && !parentOf.containsKey(parentKey);
    }

    /**
     * 沿 parentId 链上溯，检测环并给出可定位的错误信息
     */
    private static void detectCycles(EntityMeta meta, Map<String, String> parentOf) {
        for (Map.Entry<String, String> entry : parentOf.entrySet()) {
            Set<String> visited = new LinkedHashSet<>();
            String current = entry.getKey();
            while (current != null && visited.add(current)) {
                current = parentOf.get(current);
            }
            if (current != null) {
                // current 已在 visited 中：从 current 开始的整条链路构成环
                throw new QuickDevException("实体 " + meta.getEntityClass().getSimpleName()
                        + " 的树形数据存在 parentId 环（涉及 " + describeCycle(parentOf, current)
                        + "），请先修正数据；否则 JSON 序列化会无限递归");
            }
        }
    }

    private static String describeCycle(Map<String, String> parentOf, String start) {
        StringBuilder sb = new StringBuilder(start);
        String current = parentOf.get(start);
        while (current != null && !current.equals(start)) {
            sb.append(" -> ").append(current);
            current = parentOf.get(current);
        }
        return sb.append(" -> ").append(start).toString();
    }

    private static String parentKey(EntityMeta meta, Field parentField, Object node) {
        Object parentId = value(parentField, node);
        return parentId == null ? ROOT_KEY : String.valueOf(parentId);
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
