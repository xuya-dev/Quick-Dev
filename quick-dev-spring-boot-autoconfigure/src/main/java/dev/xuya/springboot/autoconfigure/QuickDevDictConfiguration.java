package dev.xuya.springboot.autoconfigure;

import dev.xuya.core.translate.DictResolver;
import dev.xuya.core.translate.DictReverseResolver;
import dev.xuya.core.translate.JdbcDictProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 内置数据库字典：字典存在数据库表（默认 sys_dict），零代码支持正向翻译与导入反向转换。
 * <ul>
 *   <li>生效条件：classpath 有 JdbcTemplate、quick-dev.dict.enabled=true（默认）、
 *       且用户未自定义 DictResolver / DictReverseResolver</li>
 *   <li>表/列名可配：quick-dev.dict.table / type-column / value-column / label-column</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "quick-dev.dict", name = "enabled", havingValue = "true", matchIfMissing = true)
public class QuickDevDictConfiguration {

    /**
     * 单一实例同时充当 DictResolver 与 DictReverseResolver（by-type 查找保持唯一候选；
     * 用户已自定义任一方向时不注册，避免与用户实现冲突）。
     */
    @Bean
    @ConditionalOnMissingBean({DictResolver.class, DictReverseResolver.class})
    public JdbcDictProvider jdbcDictProvider(QuickDevProperties properties,
                                             ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        QuickDevProperties.Dict dict = properties.getDict();
        return new JdbcDictProvider(jdbcTemplateProvider,
                dict.getTable(), dict.getTypeColumn(), dict.getValueColumn(), dict.getLabelColumn());
    }
}
