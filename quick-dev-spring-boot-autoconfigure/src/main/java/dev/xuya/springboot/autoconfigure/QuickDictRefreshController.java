package dev.xuya.springboot.autoconfigure;

import dev.xuya.core.auth.RequiresPerm;
import dev.xuya.core.common.R;
import dev.xuya.core.translate.DictCacheService;
import dev.xuya.core.translate.TranslateExecutor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内置字典缓存刷新端点：字典数据变更后调用，全量重建内存缓存。
 * <p>默认 POST /quick-dev/dict/refresh（quick-dev.dict.refresh-path 可配），
 * 需要权限码 dict:refresh（未登录 401、无权限 403）。</p>
 */
@RestController
public class QuickDictRefreshController {

    private final DictCacheService cacheService;
    private final TranslateExecutor translateExecutor;

    public QuickDictRefreshController(DictCacheService cacheService, TranslateExecutor translateExecutor) {
        this.cacheService = cacheService;
        this.translateExecutor = translateExecutor;
    }

    @RequiresPerm("dict:refresh")
    @PostMapping("${quick-dev.dict.refresh-path:/quick-dev/dict/refresh}")
    public R<Object> refresh() {
        cacheService.refresh();
        translateExecutor.clearCache(); // 同步清空翻译结果缓存，新字典立即生效
        Map<String, Object> stat = new LinkedHashMap<>();
        stat.put("size", cacheService.size());
        stat.put("loadedAt", cacheService.loadedAt());
        return R.ok("字典缓存已刷新", stat);
    }
}
