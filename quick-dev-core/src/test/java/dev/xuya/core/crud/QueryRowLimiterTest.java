package dev.xuya.core.crud;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.xuya.core.common.ParamException;
import dev.xuya.core.common.QuickDevLimits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 不分页查询的行数护栏：超限必须显式 400，而不是静默截断或全量返回
 */
class QueryRowLimiterTest {

    private final BaseMapper<Object> mapper = mock(BaseMapper.class);

    @AfterEach
    void restore() {
        QuickDevLimits.setQueryMaxRows(1_000);
    }

    @SuppressWarnings("unchecked")
    private void stubSelectPage(int rowCount) {
        when(mapper.selectPage(any(Page.class), nullable(Wrapper.class))).thenAnswer(inv -> {
            Page<Object> page = inv.getArgument(0);
            List<Object> rows = new ArrayList<>();
            for (int i = 0; i < rowCount; i++) {
                rows.add(new Object());
            }
            page.setRecords(rows);
            return page;
        });
    }

    @Test
    void rowsWithinLimitShouldBeReturned() {
        stubSelectPage(10);
        QuickDevLimits.setQueryMaxRows(10);
        assertThat(QueryRowLimiter.selectListLimited(mapper, null, "list")).hasSize(10);
    }

    @Test
    void rowsOverLimitShouldFailWith400() {
        stubSelectPage(11);
        QuickDevLimits.setQueryMaxRows(10);
        // 守卫会向数据库多要一行以判定溢出：命中 11 > 上限 10 直接报错
        assertThatThrownBy(() -> QueryRowLimiter.selectListLimited(mapper, null, "list"))
                .isInstanceOf(ParamException.class)
                .hasMessageContaining("超过上限")
                .hasMessageContaining("list");
    }

    @Test
    void missingPaginationPluginShouldFallBackToUnboundedQuery() {
        // 无分页插件时 selectPage 会抛异常：护栏降级放行（不破坏既有应用），但必须有告警日志
        when(mapper.selectPage(any(Page.class), nullable(Wrapper.class)))
                .thenThrow(new IllegalStateException("分页插件未注册"));
        when(mapper.selectList(nullable(Wrapper.class))).thenReturn(List.of(new Object()));

        List<Object> rows = QueryRowLimiter.selectListLimited(mapper, null, "tree");

        assertThat(rows).hasSize(1);
    }
}
