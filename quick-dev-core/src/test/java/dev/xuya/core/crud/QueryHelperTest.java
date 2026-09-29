package dev.xuya.core.crud;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import dev.xuya.core.annotation.QueryField;
import dev.xuya.core.annotation.QueryType;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QueryHelperTest {

    public static class SampleEntity {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String username;
        @QueryField(QueryType.LIKE)
        private String nickName;
        @QueryField(QueryType.IN)
        private Integer type;
        private Integer status;
        private LocalDateTime createTime;
    }

    private static EntityMeta meta;

    @BeforeAll
    static void init() {
        // 模拟 MyBatis-Plus 初始化 TableInfo（正常应用中由 Mapper 注册触发）
        try {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, SampleEntity.class);
        } catch (Exception ignored) {
            // 初始化失败时 EntityMeta 会退化为反射 + 驼峰转下划线，测试同样成立
        }
        meta = EntityMeta.of(SampleEntity.class);
    }

    @Test
    void entityMetaShouldResolveIdAndColumns() {
        assertThat(meta.getIdProperty()).isEqualTo("id");
        assertThat(meta.getIdType()).isEqualTo(Long.class);
        assertThat(meta.getColumn("nickName")).isEqualTo("nick_name");
        assertThat(meta.getColumn("createTime")).isEqualTo("create_time");
        assertThat(EntityMeta.camelToSnake("sysUserName")).isEqualTo("sys_user_name");
    }

    @Test
    void buildShouldTranslateParamsIntoConditions() {
        Map<String, String> params = new HashMap<>();
        params.put("username", "tom");
        params.put("nickName", "张");
        params.put("type", "1,2");
        params.put("status", "1");
        params.put("createTime", "2026-01-01T00:00:00");
        params.put("orderBy", "createTime");
        params.put("order", "desc");
        // 以下应被忽略
        params.put("current", "2");
        params.put("size", "5");
        params.put("notAField", "whatever");

        QueryWrapper<Object> wrapper = QueryHelper.build(meta, params,
                new org.springframework.format.support.DefaultFormattingConversionService());

        String sql = wrapper.getSqlSegment().toLowerCase();
        assertThat(sql).contains("username =");
        assertThat(sql).contains("nick_name like");
        assertThat(sql).contains("type in (");
        assertThat(sql).contains("status =");
        assertThat(sql).contains("create_time =");
        assertThat(sql).contains("order by create_time desc");
        assertThat(sql).doesNotContain("not_a_field");
        assertThat(sql).doesNotContain("current");

        Collection<Object> values = wrapper.getParamNameValuePairs().values();
        assertThat(values).contains("tom", "%张%", 1);
        assertThat(values).anySatisfy(v -> assertThat(v).isInstanceOf(LocalDateTime.class));
    }
}
