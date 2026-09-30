package dev.xuya.core.crud;

import dev.xuya.core.common.ParamException;
import dev.xuya.core.validation.Create;
import dev.xuya.core.validation.QuickRequire;
import dev.xuya.core.validation.Update;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 分阶段实体校验：新增（Default+Create 全实体）、修改（Default+Update 部分字段）、
 * @QuickRequire 条件必填。
 */
class EntityValidatorTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    static class Goods {
        /** 仅新增必填（修改部分语义不强制） */
        @NotBlank(groups = Create.class)
        private String name;

        /** 两阶段都校验（对提交值） */
        @Size(max = 5)
        private String remark;

        @Min(0)
        private Integer stock;

        /** 条件必填：stock=0（售罄）时必须填写原因 */
        @QuickRequire(dependField = "stock", dependValue = "0")
        private String reason;

        String getName() {
            return name;
        }

        String getRemark() {
            return remark;
        }

        Integer getStock() {
            return stock;
        }

        String getReason() {
            return reason;
        }
    }

    private static Goods goods(String name, String remark, Integer stock, String reason) {
        Goods goods = new Goods();
        goods.name = name;
        goods.remark = remark;
        goods.stock = stock;
        goods.reason = reason;
        return goods;
    }

    @Test
    void saveShouldRequireCreateGroupConstraints() {
        // 新增：name 缺失触发 Create 组 @NotBlank
        assertThatThrownBy(() -> EntityValidator.validateSave(goods(null, null, 1, null), validator))
                .isInstanceOf(ParamException.class)
                .hasMessageContaining("name");
        assertThatCode(() -> EntityValidator.validateSave(goods("手机", "现货", 1, null), validator))
                .doesNotThrowAnyException();
    }

    @Test
    void updateShouldOnlyValidateProvidedFields() {
        // 全部未提交：不更新，跳过校验
        assertThatCode(() -> EntityValidator.validateUpdate(goods(null, null, null, null), validator))
                .doesNotThrowAnyException();
        // 提交超长 remark：Update 组（未分组约束默认属于 Default，两阶段均生效）拦截
        assertThatThrownBy(() -> EntityValidator.validateUpdate(goods(null, "超长备注内容超限", 1, null), validator))
                .isInstanceOf(ParamException.class)
                .hasMessageContaining("修改参数校验失败")
                .hasMessageContaining("remark");
        // Create 组的 @NotBlank 在修改阶段不生效（语义：name 未提交 = 不更新）
        assertThatCode(() -> EntityValidator.validateUpdate(goods(null, null, 1, null), validator))
                .doesNotThrowAnyException();
        // @Min(0)：提交 stock=-5 触发 Default 组约束
        assertThatThrownBy(() -> EntityValidator.validateUpdate(goods(null, null, -5, null), validator))
                .isInstanceOf(ParamException.class)
                .hasMessageContaining("stock");
    }

    @Test
    void updateValidateSwitchShouldDisablePartialValidation() {
        EntityValidator.setUpdateValidationEnabled(false);
        try {
            assertThatCode(() -> EntityValidator.validateUpdate(goods(null, "超长备注内容超限", 1, null), validator))
                    .doesNotThrowAnyException();
        } finally {
            EntityValidator.setUpdateValidationEnabled(true);
        }
    }

    @Test
    void quickRequireShouldFireWhenDependFieldMatches() {
        // 新增：stock=-1（下架）必须填写 reason
        assertThatThrownBy(() -> EntityValidator.validateSave(goods("手机", null, 0, null), validator))
                .isInstanceOf(ParamException.class)
                .hasMessageContaining("当 stock=0 时，reason 不能为空");
        assertThatCode(() -> EntityValidator.validateSave(goods("手机", null, 0, "清仓"), validator))
                .doesNotThrowAnyException();
        // 依赖值不匹配：不要求 reason
        assertThatCode(() -> EntityValidator.validateSave(goods("手机", null, 10, null), validator))
                .doesNotThrowAnyException();
    }

    @Test
    void quickRequireOnUpdateShouldRequireProvidedDependField() {
        // 修改：本次提交 stock=-1 但未同时提交 reason -> 拦截
        assertThatThrownBy(() -> EntityValidator.validateUpdate(goods(null, null, 0, null), validator))
                .isInstanceOf(ParamException.class)
                .hasMessageContaining("当 stock=0 时，reason 不能为空");
        // 依赖字段未提交（条件状态未知）-> 跳过，交由 CrudHook 结合数据库状态判断
        assertThatCode(() -> EntityValidator.validateUpdate(goods(null, null, null, null), validator))
                .doesNotThrowAnyException();
    }
}
