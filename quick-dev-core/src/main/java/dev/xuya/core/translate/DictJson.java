package dev.xuya.core.translate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.xuya.core.common.ParamException;

import java.util.ArrayList;
import java.util.List;

/**
 * 字典标准格式（JSON）解析工具：把用户上传的 JSON 数组解析为 {@link DictEntry} 列表，
 * 配合 {@link DictCacheService#replaceAll} / {@link DictCacheService#replaceByJson}
 * 实现"上传规定格式数据，全量驻内存、零查库"。
 *
 * <pre>
 * [
 *   {"type": "sys_yes_no", "value": "Y", "label": "是"},
 *   {"type": "sys_yes_no", "value": "N", "label": "否"}
 * ]
 * </pre>
 *
 * <p>用法（自己的管理界面/导入端点中调用）：</p>
 * <pre>
 * dictCacheService.replaceByJson(request.getBody());   // 解析 + 全量替换缓存
 * </pre>
 */
public final class DictJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DictJson() {
    }

    /**
     * 解析标准 JSON 数组为字典条目。type/value/label 任一缺失即抛出
     * {@link ParamException}（HTTP 400），多余字段忽略。
     */
    public static List<DictLoader.DictEntry> parse(String json) {
        if (json == null || json.isBlank()) {
            throw new ParamException("字典数据不能为空");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new ParamException("字典数据不是合法 JSON: " + e.getLocalizedMessage(), e);
        }
        if (!root.isArray()) {
            throw new ParamException("字典数据必须是 JSON 数组");
        }
        List<DictLoader.DictEntry> entries = new ArrayList<>();
        for (JsonNode node : root) {
            String type = text(node, "type");
            String value = text(node, "value");
            String label = text(node, "label");
            if (type == null || value == null || label == null) {
                throw new ParamException(
                        "字典条目必须包含 type/value/label 字段: " + node);
            }
            entries.add(new DictLoader.DictEntry(type, value, label));
        }
        return entries;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
