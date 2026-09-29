package dev.xuya.core.crud;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 时间字段自动填充：新增时填 createTime / updateTime，修改时填 updateTime。
 *
 * <p>约定：实体属性名为 {@code createTime} / {@code updateTime}，且字段标注
 * {@code @TableField(fill = FieldFill.INSERT)}（createTime）或
 * {@code @TableField(fill = FieldFill.INSERT_UPDATE)}（updateTime）。</p>
 *
 * <p>为什么必须加 fill 注解：MyBatis-Plus 在生成 SQL 时就会按字段策略决定是否包含列，
 * 未标注 fill 的 null 字段会被动态 SQL 跳过，运行期再赋值不会生效。</p>
 *
 * <p>已有值不覆盖；支持 LocalDateTime / LocalDate / Date / Instant / Long(毫秒) 类型。</p>
 */
public class AutoFillMetaObjectHandler implements MetaObjectHandler {

    public static final String CREATE_TIME = "createTime";
    public static final String UPDATE_TIME = "updateTime";

    @Override
    public void insertFill(MetaObject metaObject) {
        fillIfAbsent(metaObject, CREATE_TIME);
        fillIfAbsent(metaObject, UPDATE_TIME);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        fillIfAbsent(metaObject, UPDATE_TIME);
    }

    private void fillIfAbsent(MetaObject metaObject, String property) {
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
}
