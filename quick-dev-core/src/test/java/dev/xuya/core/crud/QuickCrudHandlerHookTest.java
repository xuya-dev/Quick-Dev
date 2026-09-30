package dev.xuya.core.crud;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import jakarta.validation.constraints.Size;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.xuya.core.common.ParamException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import dev.xuya.core.common.R;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CrudHook 调用语义：before 在写事务内（异常回滚/不落库），after 在提交后（异常不影响响应）。
 */
class QuickCrudHandlerHookTest {

    private static EntityMeta meta;
    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private BaseMapper<Object> mapper;
    private List<String> calls;
    private List<Object> removedIds;
    private TransactionOperations tx;
    private QuickCrudHandler handler;

    @BeforeAll
    static void init() {
        try {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, SampleEntity.class);
        } catch (Exception ignored) {
            // EntityMeta 会退化为反射读取
        }
        meta = EntityMeta.of(SampleEntity.class);
    }

    @BeforeEach
    void setUp() {
        mapper = mock(BaseMapper.class);
        calls = new ArrayList<>();
        removedIds = new ArrayList<>();
        // 直接执行的"事务"：仅体现包裹关系（TransactionOperations 因受检异常无法用 lambda）
        tx = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(null);
            }
        };
        CrudHook hook = new CrudHook() {
            @Override
            public Class<?> entityType() {
                return SampleEntity.class;
            }

            @Override
            public void beforeSave(Object entity) {
                calls.add("beforeSave:" + ((SampleEntity) entity).getName());
            }

            @Override
            public void afterSave(Object entity) {
                calls.add("afterSave:" + ((SampleEntity) entity).getId());
            }

            @Override
            public void beforeUpdate(Object entity) {
                calls.add("beforeUpdate");
            }

            @Override
            public void afterUpdate(Object entity) {
                calls.add("afterUpdate");
            }

            @Override
            public void beforeRemove(List<Object> ids) {
                calls.add("beforeRemove");
                removedIds.addAll(ids);
            }

            @Override
            public void afterRemove(List<Object> ids) {
                calls.add("afterRemove");
            }
        };
        handler = new QuickCrudHandler(meta, mapper, new ObjectMapper(),
                new DefaultFormattingConversionService(), VALIDATOR, false, tx, List.of(hook));
    }

    @Test
    void saveShouldCallBeforeThenInsertThenAfter() {
        when(mapper.insert(any(SampleEntity.class))).thenAnswer(inv -> {
            SampleEntity entity = inv.getArgument(0);
            entity.setId(9L);
            return 1;
        });
        R<Object> result = handler.save("{\"name\":\"tom\"}");
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(calls).containsExactly("beforeSave:tom", "afterSave:9");
    }

    @Test
    void beforeSaveFailureShouldAbortInsert() {
        when(mapper.insert(any(SampleEntity.class))).thenThrow(new IllegalStateException("不应执行到 insert"));
        CrudHook hook = new CrudHook() {
            @Override
            public Class<?> entityType() {
                return SampleEntity.class;
            }

            @Override
            public void beforeSave(Object entity) {
                throw new IllegalStateException("校验不通过");
            }
        };
        QuickCrudHandler failing = new QuickCrudHandler(meta, mapper, new ObjectMapper(),
                new DefaultFormattingConversionService(), VALIDATOR, false, tx, List.of(hook));
        assertThatThrownBy(() -> failing.save("{\"name\":\"tom\"}"))
                .isInstanceOf(IllegalStateException.class);
        verify(mapper, never()).insert(any());
    }

    @Test
    void afterSaveFailureShouldNotFailResponse() {
        when(mapper.insert(any(SampleEntity.class))).thenReturn(1);
        CrudHook hook = new CrudHook() {
            @Override
            public Class<?> entityType() {
                return SampleEntity.class;
            }

            @Override
            public void afterSave(Object entity) {
                throw new IllegalStateException("缓存刷新失败");
            }
        };
        QuickCrudHandler failing = new QuickCrudHandler(meta, mapper, new ObjectMapper(),
                new DefaultFormattingConversionService(), VALIDATOR, false, tx, List.of(hook));
        assertThat(failing.save("{\"name\":\"tom\"}").getCode()).isEqualTo(200);
    }

    @Test
    void updateShouldRunHooksOnlyWhenRowsAffected() {
        when(mapper.updateById(any(SampleEntity.class))).thenReturn(1);
        handler.update("{\"id\":1,\"name\":\"new\"}");
        assertThat(calls).containsExactly("beforeUpdate", "afterUpdate");
    }

    @Test
    void updateShouldRejectInvalidProvidedField() {
        // name 超过 @Size(max=3)：部分更新校验拦截，不触达 Mapper
        assertThatThrownBy(() -> handler.update("{\"id\":1,\"name\":\"toolong\"}"))
                .isInstanceOf(ParamException.class)
                .hasMessageContaining("修改参数校验失败");
        verify(mapper, never()).updateById(any(SampleEntity.class));
    }

    @Test
    void removeShouldPassConvertedIds() {
        when(mapper.deleteByIds(ArgumentMatchers.<Collection<Object>>any())).thenReturn(2);
        R<Object> result = handler.remove("1,2");
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(calls).containsExactly("beforeRemove", "afterRemove");
        assertThat(removedIds).containsExactly(1L, 2L);
    }

    @Test
    void shouldWorkWithoutTransactionInfrastructure() {
        QuickCrudHandler noTx = new QuickCrudHandler(meta, mapper, new ObjectMapper(),
                new DefaultFormattingConversionService(), VALIDATOR, false, null, List.of());
        when(mapper.insert(any(SampleEntity.class))).thenReturn(1);
        assertThat(noTx.save("{\"name\":\"a\"}").getCode()).isEqualTo(200);
    }

    @TableName("sample_entity")
    static class SampleEntity {
        @TableId(type = IdType.AUTO)
        private Long id;
        @Size(max = 3)
        private String name;

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
    }
}
