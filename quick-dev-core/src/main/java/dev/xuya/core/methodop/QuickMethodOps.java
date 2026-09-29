package dev.xuya.core.methodop;

import java.lang.reflect.Method;

/**
 * 方法级注解工具：提取注解声明的完整权限码（空串视为不鉴权，返回 null）。
 */
public final class QuickMethodOps {

    private QuickMethodOps() {
    }

    /** @return 该方法上 Quick* 方法级注解要求的权限码；未标注或未配置权限返回 null */
    public static String permissionOf(Method method) {
        QuickSave save = method.getAnnotation(QuickSave.class);
        if (save != null) {
            return orNull(save.permission());
        }
        QuickUpdate update = method.getAnnotation(QuickUpdate.class);
        if (update != null) {
            return orNull(update.permission());
        }
        QuickRemove remove = method.getAnnotation(QuickRemove.class);
        if (remove != null) {
            return orNull(remove.permission());
        }
        QuickExport export = method.getAnnotation(QuickExport.class);
        if (export != null) {
            return orNull(export.permission());
        }
        QuickImport importExcel = method.getAnnotation(QuickImport.class);
        if (importExcel != null) {
            return orNull(importExcel.permission());
        }
        return null;
    }

    private static String orNull(String permission) {
        return permission == null || permission.isEmpty() ? null : permission;
    }
}
