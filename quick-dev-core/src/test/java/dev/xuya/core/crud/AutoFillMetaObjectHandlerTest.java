package dev.xuya.core.crud;

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
        private String name;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public LocalDateTime getCreateTime() { return createTime; }
        public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
        public LocalDateTime getUpdateTime() { return updateTime; }
        public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
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
}
