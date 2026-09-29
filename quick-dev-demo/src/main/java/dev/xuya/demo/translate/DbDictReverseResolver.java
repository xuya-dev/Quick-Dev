package dev.xuya.demo.translate;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import dev.xuya.core.translate.DictReverseResolver;
import dev.xuya.demo.entity.SysDict;
import dev.xuya.demo.mapper.SysDictMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 字典反解实现（"上传转换"）：标签 -> 值。Excel 导入时用户填中文标签，
 * 框架调用本实现反解回库存值。
 */
@Service
public class DbDictReverseResolver implements DictReverseResolver {

    private final SysDictMapper dictMapper;

    public DbDictReverseResolver(SysDictMapper dictMapper) {
        this.dictMapper = dictMapper;
    }

    @Override
    public Object reverse(String dictType, String label) {
        List<SysDict> dicts = dictMapper.selectList(new QueryWrapper<SysDict>()
                .eq("dict_type", dictType)
                .eq("dict_label", label)
                .last("limit 1"));
        return dicts.isEmpty() ? null : dicts.get(0).getDictValue();
    }
}
