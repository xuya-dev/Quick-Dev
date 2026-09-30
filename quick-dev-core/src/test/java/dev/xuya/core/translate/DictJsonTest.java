package dev.xuya.core.translate;

import dev.xuya.core.common.ParamException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DictJsonTest {

    @Test
    void parseShouldReadStandardArrayFormat() {
        List<DictLoader.DictEntry> entries = DictJson.parse(
                "[{\"type\":\"sys_yes_no\",\"value\":\"Y\",\"label\":\"是\"}," +
                 "{\"type\":\"sys_yes_no\",\"value\":\"N\",\"label\":\"否\",\"extra\":1}]");
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).type()).isEqualTo("sys_yes_no");
        assertThat(entries.get(0).value()).isEqualTo("Y");
        assertThat(entries.get(0).label()).isEqualTo("是");
        assertThat(entries.get(1).label()).isEqualTo("否");
    }

    @Test
    void parseShouldRejectMalformedInput() {
        assertThatThrownBy(() -> DictJson.parse("not json"))
                .isInstanceOf(ParamException.class)
                .hasMessageContaining("不是合法 JSON");
        assertThatThrownBy(() -> DictJson.parse("{\"type\":\"t\"}"))
                .isInstanceOf(ParamException.class)
                .hasMessageContaining("JSON 数组");
        assertThatThrownBy(() -> DictJson.parse("[{\"type\":\"t\",\"value\":\"v\"}]"))
                .isInstanceOf(ParamException.class)
                .hasMessageContaining("type/value/label");
        assertThatThrownBy(() -> DictJson.parse("  "))
                .isInstanceOf(ParamException.class)
                .hasMessageContaining("不能为空");
    }
}
