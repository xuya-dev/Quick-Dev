package dev.xuya.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.xuya.core.annotation.QueryField;
import dev.xuya.core.annotation.QueryType;
import dev.xuya.core.translate.Translate;
import dev.xuya.core.translate.TranslateMode;
import dev.xuya.core.validation.Create;
import dev.xuya.core.validation.QuickRequire;
import dev.xuya.core.validation.Update;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 0.4.0 特性演示实体：
 * <ul>
 *   <li>分阶段校验——name 仅新增必填（Create 组），stock 两阶段都校验（Default 组）；</li>
 *   <li>@QuickRequire 条件必填——status=0（售罄）时 reason 必填；</li>
 *   <li>@Translate APPEND——status 保留原值并附加 statusName 文本；</li>
 *   <li>写路径由 GoodsHook 做规范化（非售罄自动清理 reason）；</li>
 *   <li>delFlag 逻辑删除字段：写路径由框架强制清空，客户端无法通过接口操纵。</li>
 * </ul>
 */
@TableName("demo_goods")
public class Goods {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(groups = Create.class, message = "新增时商品名必填")
    @Size(max = 50, groups = {Create.class, Update.class})
    @QueryField(QueryType.LIKE)
    private String name;

    @Size(max = 200, groups = {Create.class, Update.class})
    private String remark;

    @NotNull(groups = {Create.class, Update.class})
    @Min(value = 0, groups = {Create.class, Update.class})
    private Integer stock;

    /** 条件必填：售罄（status=0）时必须填写原因 */
    @QuickRequire(dependField = "status", dependValue = "0",
            groups = {Create.class, Update.class},
            message = "商品售罄时必须填写下架原因")
    private String reason;

    /**
     * APPEND 附加模式：status 保留原值（编辑表单可用），另附 statusName="售罄" 文本
     */
    @Translate(enumClass = GoodsStatus.class, mode = TranslateMode.APPEND)
    private Integer status;

    /** 逻辑删除字段：写路径由框架强制清空，客户端无法通过接口删除/复活记录 */
    @TableLogic
    @JsonIgnore
    private Integer delFlag;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
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
