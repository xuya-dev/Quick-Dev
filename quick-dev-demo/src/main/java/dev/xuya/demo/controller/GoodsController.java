package dev.xuya.demo.controller;

import dev.xuya.core.annotation.CrudOp;
import dev.xuya.core.annotation.QuickCrud;
import dev.xuya.demo.entity.Goods;
import org.springframework.web.bind.annotation.RestController;

/**
 * 0.4.0 特性演示：分阶段校验（Create/Update 分组 + @QuickRequire）、CrudHook 规范化
 * 与 delFlag 保护均在实体/钩子侧声明，Controller 本体保持一行注解。
 */
@RestController
@QuickCrud(entity = Goods.class, includes = {CrudOp.PAGE, CrudOp.DETAIL,
        CrudOp.SAVE, CrudOp.UPDATE, CrudOp.REMOVE})
public class GoodsController {
}
