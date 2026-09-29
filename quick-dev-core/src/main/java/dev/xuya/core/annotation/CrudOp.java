package dev.xuya.core.annotation;

import org.springframework.web.bind.annotation.RequestMethod;

/**
 * @QuickCrud 支持的内置操作。
 */
public enum CrudOp {

    /** GET {base}/page 分页查询 */
    PAGE("page", "/page", RequestMethod.GET, "list"),
    /** GET {base}/list 列表查询（不分页） */
    LIST("list", "/list", RequestMethod.GET, "list"),
    /** GET {base}/count 按条件统计数量 */
    COUNT("count", "/count", RequestMethod.GET, "list"),
    /** GET {base}/{id} 详情 */
    DETAIL("detail", "/{id}", RequestMethod.GET, "detail"),
    /** POST {base} 新增 */
    SAVE("save", "", RequestMethod.POST, "add"),
    /** PUT {base} 修改（按 ID 全量/非空更新） */
    UPDATE("update", "", RequestMethod.PUT, "edit"),
    /** DELETE {base}/{ids} 删除，ids 逗号分隔支持批量 */
    REMOVE("remove", "/{ids}", RequestMethod.DELETE, "remove");

    /** Handler 方法名 */
    private final String handlerMethod;
    /** 追加到 base 路径后的子路径 */
    private final String path;
    /** HTTP 方法 */
    private final RequestMethod requestMethod;
    /** 权限码后缀（拼接在 @QuickCrud.permission 之后），例如 "sys:user" + ":" + "list" */
    private final String permissionSuffix;

    CrudOp(String handlerMethod, String path, RequestMethod requestMethod, String permissionSuffix) {
        this.handlerMethod = handlerMethod;
        this.path = path;
        this.requestMethod = requestMethod;
        this.permissionSuffix = permissionSuffix;
    }

    public String getHandlerMethod() {
        return handlerMethod;
    }

    public String getPath() {
        return path;
    }

    public RequestMethod getRequestMethod() {
        return requestMethod;
    }

    public String getPermissionSuffix() {
        return permissionSuffix;
    }
}
