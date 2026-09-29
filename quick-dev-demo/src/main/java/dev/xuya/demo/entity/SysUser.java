package dev.xuya.demo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.xuya.core.annotation.QueryField;
import dev.xuya.core.annotation.QueryType;
import dev.xuya.core.datascope.DataScope;
import dev.xuya.core.translate.Translate;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * 系统用户（同时演示：Long 自增主键、LIKE 模糊查询、参数校验、时间/操作人自动填充、
 * 字段翻译、行级数据权限——普通用户只能看到本部门数据）
 */
@DataScope(column = "dept_id")
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "用户名不能为空")
    @QueryField(QueryType.LIKE)
    private String username;

    @QueryField(QueryType.LIKE)
    private String nickname;

    /** 演示环境为方便使用明文存储，生产环境请使用 BCrypt 等哈希算法 */
    @JsonIgnore
    private String password;

    @NotBlank(message = "邮箱不能为空")
    private String email;

    /** 字典翻译：1 -> 启用，0 -> 停用 */
    @Translate(dict = "user_status")
    private Integer status;

    /** 所属部门（数据权限按此列过滤） */
    private Long deptId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 关联翻译：loginId -> 用户昵称（"1" -> "管理员"） */
    @Translate(entity = SysUser.class, field = "nickname")
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @Translate(entity = SysUser.class, field = "nickname")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
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

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }
}
