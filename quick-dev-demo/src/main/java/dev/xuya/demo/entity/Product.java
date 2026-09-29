package dev.xuya.demo.entity;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import dev.xuya.core.annotation.QueryField;
import dev.xuya.core.annotation.QueryType;
import dev.xuya.core.translate.Translate;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品（同时演示：String UUID 主键、BigDecimal、范围查询、时间自动填充、Excel 导入导出列名）
 */
@TableName("product")
public class Product {

    /** 雪花/UUID 主键，插入时 MP 自动回填 */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    @ExcelProperty("商品名称")
    @NotBlank(message = "商品名称不能为空")
    @QueryField(QueryType.LIKE)
    private String name;

    /** 枚举字典翻译：1 -> 普通商品，2 -> 赠品 */
    @Translate(enumClass = ProductType.class)
    private Integer type;

    @ExcelProperty("价格")
    private BigDecimal price;

    @ExcelProperty("库存")
    @QueryField(QueryType.GE)
    private Integer stock;

    @QueryField(QueryType.BETWEEN)
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
