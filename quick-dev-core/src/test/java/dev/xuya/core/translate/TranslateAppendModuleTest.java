package dev.xuya.core.translate;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * APPEND 附加模式：字段保留原值，翻译结果追加到兄弟字段；REPLACE 默认行为不变。
 */
class TranslateAppendModuleTest {

    private static final ObjectMapper MAPPER_WITH_MODULE =
            new ObjectMapper().registerModule(new TranslateAppendModule());

    @BeforeAll
    static void initExecutor() {
        TranslateExecutor.register(new TranslateExecutor(true, 0));
    }

    @AfterAll
    static void clearExecutor() {
        TranslateExecutor.register(null);
    }

    enum PayStatus implements DictEnum {
        PAID(1, "已支付"), REFUNDED(2, "已退款");

        private final Integer value;
        private final String label;

        PayStatus(Integer value, String label) {
            this.value = value;
            this.label = label;
        }

        @Override
        public Object getValue() {
            return value;
        }

        @Override
        public String getLabel() {
            return label;
        }
    }

    static class Order {
        /** REPLACE（默认）：值被替换为标签 */
        @Translate(enumClass = PayStatus.class)
        private Integer status = 1;

        /** APPEND：保留原值，追加默认兄弟字段 statusName */
        @Translate(enumClass = PayStatus.class, mode = TranslateMode.APPEND)
        private Integer payStatus = 2;

        /** APPEND：显式指定兄弟字段名 */
        @Translate(enumClass = PayStatus.class, mode = TranslateMode.APPEND, appendField = "channelText")
        private Integer channel = 1;

        public Integer getStatus() {
            return status;
        }

        public Integer getPayStatus() {
            return payStatus;
        }

        public Integer getChannel() {
            return channel;
        }
    }

    @Test
    void appendShouldKeepOriginalValueAndAddSiblingField() throws Exception {
        String json = MAPPER_WITH_MODULE.writeValueAsString(new Order());
        assertThat(json).contains("\"status\":\"已支付\"");
        assertThat(json).contains("\"payStatus\":2");
        assertThat(json).contains("\"payStatusName\":\"已退款\"");
        assertThat(json).contains("\"channel\":1");
        assertThat(json).contains("\"channelText\":\"已支付\"");
    }

    @Test
    void replaceShouldStayDefaultWithoutModule() throws Exception {
        String json = new ObjectMapper().writeValueAsString(new Order());
        assertThat(json).contains("\"status\":\"已支付\"");
        // APPEND 字段在没有模块时仅透传原值（无兄弟属性）
        assertThat(json).contains("\"payStatus\":2");
        assertThat(json).doesNotContain("payStatusName");
    }

    @Test
    void noExecutorShouldTranslateNothing() throws Exception {
        try {
            TranslateExecutor.register(null);
            String json = MAPPER_WITH_MODULE.writeValueAsString(new Order());
            // 原值保留；无翻译结果时兄弟字段输出 null
            assertThat(json).contains("\"status\":1");
            assertThat(json).contains("\"payStatusName\":null");
        } finally {
            TranslateExecutor.register(new TranslateExecutor(true, 0));
        }
    }
}
