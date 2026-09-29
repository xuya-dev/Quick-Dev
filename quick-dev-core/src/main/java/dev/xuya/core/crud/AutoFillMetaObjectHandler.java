package dev.xuya.core.crud;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import dev.xuya.core.auth.AuthContext;
import org.apache.ibatis.reflection.MetaObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 时间与操作人字段自动填充。
 *
 * <p>新增时填 createTime/updateTime/createBy/updateBy，修改时填 updateTime/updateBy；
 * 已有值一律不覆盖；未登录（无操作人上下文）时跳过操作人字段。</p>
 *
 * <p>约定：字段标注 {@code @TableField(fill = FieldFill.INSERT)}（createTime/createBy）或
 * {@code @TableField(fill = FieldFill.INSERT_UPDATE)}（updateTime/updateBy）。
 * 时间字段支持 LocalDateTime / LocalDate / Date / Instant / Long(毫秒)；
 * 操作人字段须为 String 类型，取当前登录人（Sa-Token 模式下为 loginId）。</p>
 *
 * <p>为什么必须加 fill 注解：MyBatis-Plus 在生成 SQL 时就会按字段策略决定是否包含列，
 * 未标注 fill 的 null 字段会被动态 SQL 跳过，运行期再赋值不会生效。</p>
 */
public class AutoFillMetaObjectHandler implements MetaObjectHandler {

    public static final String CREATE_TIME = "createTime";
    public static final String UPDATE_TIME = "updateTime";
    public static final String CREATE_BY = "createBy";
    public static final String UPDATE_BY = "updateBy";

    @Override
    public void insertFill(MetaObject metaObject) {
        fillTimeIfAbsent(metaObject, CREATE_TIME);
        fillTimeIfAbsent(metaObject, UPDATE_TIME);
        fillOperatorIfAbsent(metaObject, CREATE_BY);
        fillOperatorIfAbsent(metaObject, UPDATE_BY);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        fillTimeIfAbsent(metaObject, UPDATE_TIME);
        fillOperatorIfAbsent(metaObject, UPDATE_BY);
    }

    private void fillTimeIfAbsent(MetaObject metaObject, String property) {
        if (!metaObject.hasSetter(property) || metaObject.getValue(property) != null) {
            return;
        }
        Class<?> type = metaObject.getSetterType(property);
        Object now;
        if (type == LocalDateTime.class) {
            now = LocalDateTime.now();
        } else if (type == LocalDate.class) {
            now = LocalDate.now();
        } else if (type == Date.class) {
            now = new Date();
        } else if (type == Instant.class) {
            now = Instant.now();
        } else if (type == Long.class || type == long.class) {
            now = System.currentTimeMillis();
        } else {
            return; // 类型不支持，跳过
        }
        metaObject.setValue(property, now);
    }

    private void fillOperatorIfAbsent(MetaObject metaObject, String property) {
        if (!metaObject.hasSetter(property) || metaObject.getValue(property) != null) {
            return;
        }
        if (metaObject.getSetterType(property) != String.class) {
            return; // 操作人字段仅支持 String
        }
        Object operator = AuthContext.getUser();
        if (operator == null) {
            return; // 未登录（如定时任务、open 接口）不填
        }
        metaObject.setValue(property, String.valueOf(operator));
    }
}
