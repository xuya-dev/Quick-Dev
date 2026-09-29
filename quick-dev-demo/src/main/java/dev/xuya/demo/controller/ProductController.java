package dev.xuya.demo.controller;

import dev.xuya.core.annotation.CrudOp;
import dev.xuya.core.annotation.QuickCrud;
import dev.xuya.core.common.R;
import dev.xuya.core.methodop.*;
import dev.xuya.demo.entity.Product;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 商品：类级与方法级混用演示。
 * <p>查询类接口（page/count/detail/导入模板）由 @QuickCrud 自动注册；
 * 写操作与导入导出用方法级注解 —— 方法体留空，框架 AOP 接管执行。</p>
 */
@RestController
@RequestMapping("/product")
@QuickCrud(entity = Product.class,
        includes = {CrudOp.PAGE, CrudOp.COUNT, CrudOp.DETAIL, CrudOp.IMPORT_TEMPLATE})
public class ProductController {

    /**
     * 新增：POST /product（开放，不鉴权）
     */
    @QuickSave(entity = Product.class)
    @PostMapping
    public R<Object> save(@RequestBody Product product) {
        return null; // 由框架执行 insert 并返回 R
    }

    /**
     * 修改：PUT /product
     */
    @QuickUpdate(entity = Product.class)
    @PutMapping
    public R<Object> update(@RequestBody Product product) {
        return null;
    }

    /**
     * 删除：DELETE /product/{ids}（支持逗号分隔批量）
     */
    @QuickRemove(entity = Product.class)
    @DeleteMapping("/{ids}")
    public R<Object> remove(@PathVariable("ids") String ids) {
        return null;
    }

    /**
     * 导出：GET /product/export（复用 page 的查询条件；translate 导出翻译后的中文标签）
     */
    @QuickExport(entity = Product.class, translate = true)
    @GetMapping("/export")
    public void export(HttpServletResponse response) {
        // 由框架查询并写出 Excel
    }

    /**
     * 导入：POST /product/import（multipart 字段 file；演示方法级权限码）
     */
    @QuickImport(entity = Product.class, permission = "product:import")
    @PostMapping("/import")
    public R<Object> importExcel(MultipartFile file) {
        return null;
    }
}
