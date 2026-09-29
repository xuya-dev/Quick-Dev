package dev.xuya.demo.controller;

import dev.xuya.core.annotation.CrudOp;
import dev.xuya.core.annotation.QuickCrud;
import dev.xuya.demo.entity.Product;

/**
 * 商品：不设置 permission、也不要求登录 —— 完全开放的只读 + 增删改示例，
 * 同时演示 excludes 排除操作（这里排除 LIST，只保留其余 5 个接口）。
 */
@QuickCrud(entity = Product.class, excludes = CrudOp.LIST)
public class ProductController {
}
