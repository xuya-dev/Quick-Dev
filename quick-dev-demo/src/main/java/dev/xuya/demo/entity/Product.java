package dev.xuya.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import dev.xuya.core.annotation.QueryField;
import dev.xuya.core.annotation.QueryType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品（同时演示：String UUID 主键、BigDecimal、范围查询）
 */
@TableName("product")
public class Product {

    /** 雪花/UUID 主键，插入时 MP 自动回填 */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    @QueryField(QueryType.LIKE)
    private String name;

    private BigDecimal price;

    @QueryField(QueryType.GE)
    private Integer stock;

    private LocalDateTime createTime;

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
}
