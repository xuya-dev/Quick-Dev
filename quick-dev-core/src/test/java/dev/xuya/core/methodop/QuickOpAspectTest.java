package dev.xuya.core.methodop;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.xuya.core.common.ParamException;
import dev.xuya.core.common.R;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 方法级注解切面的直接单测（demo 端到端之外的路径与错误分支）：
 * 实体参数定位、主键校验、ids 转换、clearSystemFields 防伪造、无注解回退 proceed。
 * 批量插入走 Db.saveBatch 静态工具需 MP 运行时上下文，由 demo 集成测试覆盖。
 */
class QuickOpAspectTest {

    private final ApplicationContext applicationContext = mock(ApplicationContext.class);
    private final BaseMapper<Object> mapper = mock(BaseMapper.class);
    private final QuickOpAspect aspect = new QuickOpAspect(applicationContext);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void stubContext() {
        // MapperResolver：按 BaseMapper<Sample> 泛型找 mapper
        when(applicationContext.getBeanNamesForType(any(ResolvableType.class)))
                .thenReturn(new String[]{"sampleMapper"});
        when(applicationContext.getBean("sampleMapper")).thenReturn(mapper);

        ObjectProviderStub.validator(applicationContext);
        ObjectProviderStub.transaction(applicationContext);
        // conversionService：取不到 mvcConversionService 时切面回退默认实现
        when(applicationContext.getBean(anyString(), any(Class.class)))
                .thenThrow(new org.springframework.beans.factory.NoSuchBeanDefinitionException("x"));
    }

    // ------------------------------------------------------------------
    // @QuickSave
    // ------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void saveShouldClearSystemFieldsInsertAndReturnEntity() throws Throwable {
        when(mapper.insert(any(Sample.class))).thenReturn(1);
        Sample entity = new Sample("tom");
        entity.setCreateBy("hacker"); // 客户端伪造的审计归属必须被清空

        R<Object> result = (R<Object>) aspect.around(joinPoint("save", entity));

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isSameAs(entity);
        verify(mapper).insert(any(Sample.class));
        // clearSystemFields：createBy/updateBy 置空，由 AutoFill 按登录人回填
        assertThat(entity.getCreateBy()).isNull();
    }

    @Test
    void saveWithoutEntityArgShouldFailWithClearMessage() {
        assertThatThrownBy(() -> aspect.around(joinPoint("save")))
                .isInstanceOf(ParamException.class)
                .hasMessageContaining("Sample");
    }

    // ------------------------------------------------------------------
    // @QuickUpdate
    // ------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void updateShouldValidateAndReturnAffectedFlag() throws Throwable {
        when(mapper.updateById(any(Sample.class))).thenReturn(1);
        Sample entity = new Sample("tom");
        entity.setId(7L);

        R<Object> result = (R<Object>) aspect.around(joinPoint("update", entity));

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isEqualTo(true);
        verify(mapper).updateById(any(Sample.class));
    }

    @Test
    void updateWithoutIdShouldFailFast() {
        assertThatThrownBy(() -> aspect.around(joinPoint("update", new Sample("tom"))))
                .isInstanceOf(ParamException.class)
                .hasMessageContaining("主键");
    }

    // ------------------------------------------------------------------
    // @QuickRemove
    // ------------------------------------------------------------------

    @Test
    void removeShouldConvertCommaSeparatedIdsAndDelete() throws Throwable {
        when(mapper.deleteBatchIds(any(Collection.class))).thenReturn(2);

        R<Object> result = (R<Object>) aspect.around(joinPoint("remove", "1, 2"));

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isEqualTo(2);
        verify(mapper).deleteBatchIds(List.of(1L, 2L));
    }

    @Test
    void removeWithoutIdsArgShouldFailFast() {
        assertThatThrownBy(() -> aspect.around(joinPoint("remove")))
                .isInstanceOf(ParamException.class)
                .hasMessageContaining("ids");
    }

    // ------------------------------------------------------------------
    // 兜底：方法没有方法级注解时透传原方法
    // ------------------------------------------------------------------

    @Test
    void methodWithoutQuickAnnotationShouldProceed() throws Throwable {
        ProceedingJoinPoint pjp = joinPoint("plain");
        when(pjp.proceed()).thenReturn("raw");

        assertThat(aspect.around(pjp)).isEqualTo("raw");
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private ProceedingJoinPoint joinPoint(String methodName, Object... args) throws NoSuchMethodException {
        for (Method method : SampleController.class.getMethods()) {
            if (method.getName().equals(methodName)) {
                MethodSignature signature = mock(MethodSignature.class);
                when(signature.getMethod()).thenReturn(method);

                ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
                when(pjp.getSignature()).thenReturn(signature);
                when(pjp.getArgs()).thenReturn(args);
                return pjp;
            }
        }
        throw new NoSuchMethodException(methodName);
    }

    /**
     * ObjectProvider 的公共打桩（Validator / TransactionOperations）
     */
    static final class ObjectProviderStub {

        static void validator(ApplicationContext ctx) {
            org.springframework.beans.factory.ObjectProvider<Validator> provider = mock(org.springframework.beans.factory.ObjectProvider.class);
            when(ctx.getBeanProvider(Validator.class)).thenReturn(provider);
            when(provider.getIfAvailable())
                    .thenReturn(Validation.buildDefaultValidatorFactory().getValidator());
        }

        static void transaction(ApplicationContext ctx) {
            org.springframework.beans.factory.ObjectProvider<TransactionOperations> provider = mock(org.springframework.beans.factory.ObjectProvider.class);
            when(ctx.getBeanProvider(TransactionOperations.class)).thenReturn(provider);
            when(provider.getIfAvailable()).thenReturn(new TransactionOperations() {
                @Override
                public <T> T execute(TransactionCallback<T> action) {
                    return action.doInTransaction(null);
                }
            });
        }
    }

    @TableName("aspect_sample")
    static class Sample {

        @TableId(type = IdType.AUTO)
        private Long id;
        private String name;
        private String createBy;
        private String updateBy;

        Sample(String name) {
            this.name = name;
        }

        Long getId() {
            return id;
        }

        void setId(Long id) {
            this.id = id;
        }

        String getName() {
            return name;
        }

        String getCreateBy() {
            return createBy;
        }

        void setCreateBy(String createBy) {
            this.createBy = createBy;
        }

        String getUpdateBy() {
            return updateBy;
        }

        void setUpdateBy(String updateBy) {
            this.updateBy = updateBy;
        }
    }

    static class SampleController {

        @QuickSave(entity = Sample.class)
        public Object save(Sample entity) {
            return null;
        }

        @QuickUpdate(entity = Sample.class)
        public Object update(Sample entity) {
            return null;
        }

        @QuickRemove(entity = Sample.class)
        public Object remove(String ids) {
            return null;
        }

        public Object plain() {
            return null;
        }
    }
}
