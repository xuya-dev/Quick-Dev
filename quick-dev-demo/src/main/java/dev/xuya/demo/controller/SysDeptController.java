package dev.xuya.demo.controller;

import dev.xuya.core.annotation.CrudOp;
import dev.xuya.core.annotation.QuickCrud;
import dev.xuya.demo.entity.SysDept;

/**
 * 部门树演示：CrudOp.TREE 自动注册 GET /sys-dept/tree
 */
@QuickCrud(entity = SysDept.class,
        includes = {CrudOp.TREE, CrudOp.PAGE, CrudOp.COUNT, CrudOp.SAVE, CrudOp.REMOVE})
public class SysDeptController {
}
