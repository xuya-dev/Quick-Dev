package dev.xuya.demo.translate;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import dev.xuya.core.translate.DictLoader;
import dev.xuya.demo.entity.SysDict;
import dev.xuya.demo.mapper.SysDictMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 演示方自定义字典数据源：实现 DictLoader 查自己的 sys_dict 表。
 * <p>框架自身不查任何数据库——数据来源完全由使用方决定
 * （数据库表 / 远程字典服务 / 配置中心，或直接走导入端点上传）。</p>
 */
@Component
public class DbDictLoader implements DictLoader {

    private final SysDictMapper dictMapper;

    public DbDictLoader(SysDictMapper dictMapper) {
        this.dictMapper = dictMapper;
    }

    @Override
    public List<DictEntry> loadAll() {
        return dictMapper.selectList(new QueryWrapper<SysDict>().orderByAsc("dict_type", "dict_value"))
                .stream()
                .map(d -> new DictEntry(d.getDictType(), d.getDictValue(), d.getDictLabel()))
                .toList();
    }
}
