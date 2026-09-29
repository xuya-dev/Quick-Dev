package dev.xuya.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 用户-权限关联（演示项目简化模型，生产环境建议完整的 用户-角色-权限 三表 RBAC）
 */
@TableName("sys_user_perm")
public class SysUserPerm {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /**
     * 权限码，* 表示超级权限
     */
    private String permCode;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getPermCode() {
        return permCode;
    }

    public void setPermCode(String permCode) {
        this.permCode = permCode;
    }
}
