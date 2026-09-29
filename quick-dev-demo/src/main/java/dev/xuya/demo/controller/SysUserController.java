package dev.xuya.demo.controller;

import dev.xuya.core.annotation.QuickCrud;
import dev.xuya.demo.entity.SysUser;

/**
 * 用户管理：一个注解 = 7 个带权限控制的 CRUD 接口。
 * <p>自动注册（base 路径由实体名推导为 /sys-user）：</p>
 * <pre>
 * GET    /sys-user/page        分页（需 sys:user:list）
 * GET    /sys-user/list        列表（需 sys:user:list）
 * GET    /sys-user/count       统计（需 sys:user:list）
 * GET    /sys-user/{id}        详情（需 sys:user:detail）
 * POST   /sys-user             新增（需 sys:user:add）
 * PUT    /sys-user             修改（需 sys:user:edit）
 * DELETE /sys-user/{ids}       删除，支持批量（需 sys:user:remove）
 * </pre>
 */
@QuickCrud(entity = SysUser.class, permission = "sys:user")
public class SysUserController {
    // 空的：不需要写任何方法。也可以在这里继续加自定义接口。
}
