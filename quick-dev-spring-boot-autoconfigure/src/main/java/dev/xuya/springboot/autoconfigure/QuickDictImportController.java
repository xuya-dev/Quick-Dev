package dev.xuya.springboot.autoconfigure;

import dev.xuya.core.auth.RequiresPerm;
import dev.xuya.core.common.R;
import dev.xuya.core.translate.DictCacheService;
import dev.xuya.core.translate.DictLoader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 字典数据导入端点：用户按框架规定格式（[{type, value, label}, ...]）POST 全量数据，
 * 框架直接替换内存缓存——**不查任何数据库**，数据来源完全由用户决定。
 *
 * <pre>
 * POST /quick-dev/dict/import   （需 dict:import 权限码）
 * [{"type":"user_status","value":"1","label":"启用"},
 *  {"type":"user_status","value":"0","label":"停用"}]
 * </pre>
 *
 * <p>全量替换而非增量合并；导入后新数据立即生效（翻译结果缓存同步清空）。</p>
 */
@RestController
public class QuickDictImportController {

    private final DictCacheService cacheService;

    public QuickDictImportController(DictCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @RequiresPerm("dict:import")
    @PostMapping("${quick-dev.dict.import-path:/quick-dev/dict/import}")
    public R<Object> importEntries(@RequestBody List<DictLoader.DictEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return R.fail(400, "字典数据不能为空");
        }
        cacheService.replaceAll(entries);
        return R.ok("导入成功", Map.of("size", cacheService.size()));
    }
}
