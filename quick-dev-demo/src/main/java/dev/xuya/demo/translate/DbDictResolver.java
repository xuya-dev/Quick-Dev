package dev.xuya.demo.translate;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import dev.xuya.core.translate.DictResolver;
import dev.xuya.demo.entity.SysDict;
import dev.xuya.demo.mapper.SysDictMapper;
import org.springframework.stereotype.Service;

/**
 * 字典翻译实现：查 sys_dict 表（框架侧有 TTL 缓存，这里无需自己缓存）。
 */
@Service
public class DbDictResolver implements DictResolver {

    private final SysDictMapper dictMapper;

    public DbDictResolver(SysDictMapper dictMapper) {
        this.dictMapper = dictMapper;
    }

    @Override
    public String resolve(String dictType, Object dictValue) {
        SysDict dict = dictMapper.selectOne(new QueryWrapper<SysDict>()
                .eq("dict_type", dictType)
                .eq("dict_value", String.valueOf(dictValue))
                .last("limit 1"));
        return dict == null ? null : dict.getDictLabel();
    }
}
