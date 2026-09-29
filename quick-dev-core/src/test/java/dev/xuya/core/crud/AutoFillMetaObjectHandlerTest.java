package dev.xuya.core.crud;

import dev.xuya.core.auth.AuthContext;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AutoFillMetaObjectHandlerTest {

    public static class SampleEntity {
        private Long id;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private String createBy;
        private String updateBy;
        private String name;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public LocalDateTime getCreateTime() { return createTime; }
        public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
        public LocalDateTime getUpdateTime() { return updateTime; }
        public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
        public String getCreateBy() { return createBy; }
        public void setCreateBy(String createBy) { this.createBy = createBy; }
        public String getUpdateBy() { return updateBy; }
        public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    private final AutoFillMetaObjectHandler handler = new AutoFillMetaObjectHandler();

    @Test
    void insertFillShouldSetCreateAndUpdateTime() {
        SampleEntity entity = new SampleEntity();
        handler.insertFill(SystemMetaObject.forObject(entity));
        assertThat(entity.getCreateTime()).isNotNull();
        assertThat(entity.getUpdateTime()).isNotNull();
    }

    @Test
    void updateFillShouldOnlyTouchUpdateTime() {
        SampleEntity entity = new SampleEntity();
        handler.updateFill(SystemMetaObject.forObject(entity));
        assertThat(entity.getUpdateTime()).isNotNull();
        assertThat(entity.getCreateTime()).isNull();
    }

    @Test
    void existingValueShouldNotBeOverwritten() {
        SampleEntity entity = new SampleEntity();
        LocalDateTime fixed = LocalDateTime.of(2020, 1, 1, 0, 0);
        entity.setUpdateTime(fixed);
        MetaObject metaObject = SystemMetaObject.forObject(entity);
        handler.insertFill(metaObject);
        handler.updateFill(metaObject);
        assertThat(entity.getUpdateTime()).isEqualTo(fixed);
    }

    @Test
    void entityWithoutTimeFieldsShouldBeIgnoredSafely() {
        MetaObject metaObject = SystemMetaObject.forObject("plain-string");
        handler.insertFill(metaObject); // 不抛异常即可
        handler.updateFill(metaObject);
    }

    @Test
    void operatorShouldBeFilledWhenLoggedIn() {
        AuthContext.set(1L, "test-token");
        try {
            SampleEntity entity = new SampleEntity();
            handler.insertFill(SystemMetaObject.forObject(entity));
            assertThat(entity.getCreateBy()).isEqualTo("1");
            assertThat(entity.getUpdateBy()).isEqualTo("1");

            entity.setUpdateBy(null);
            handler.updateFill(SystemMetaObject.forObject(entity));
            assertThat(entity.getUpdateBy()).isEqualTo("1");
        } finally {
            AuthContext.clear();
        }
    }

    @Test
    void operatorShouldBeSkippedWhenAnonymous() {
        SampleEntity entity = new SampleEntity();
        handler.insertFill(SystemMetaObject.forObject(entity));
        assertThat(entity.getCreateBy()).isNull();
        assertThat(entity.getUpdateBy()).isNull();
        // 时间字段不受登录态影响
        assertThat(entity.getCreateTime()).isNotNull();
    }
}
