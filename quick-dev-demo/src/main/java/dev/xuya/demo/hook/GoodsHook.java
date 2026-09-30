package dev.xuya.demo.hook;

import dev.xuya.core.crud.CrudHook;
import dev.xuya.demo.entity.Goods;
import org.springframework.stereotype.Component;

/**
 * CrudHook 演示：Goods 的写流程聚合逻辑（无需手写 Controller）。
 *
 * <pre>
 * beforeSave/beforeUpdate：业务规范化——非售罄（stock != 0）时自动清理 reason；
 * afterSave/afterUpdate/afterRemove：真实项目里可在此刷新缓存/发事件（失败不影响请求）。
 * </pre>
 */
@Component
public class GoodsHook implements CrudHook {

    @Override
    public Class<?> entityType() {
        return Goods.class;
    }

    @Override
    public void beforeSave(Object entity) {
        normalize((Goods) entity);
    }

    @Override
    public void beforeUpdate(Object entity) {
        normalize((Goods) entity);
    }

    private void normalize(Goods goods) {
        if (goods.getStock() != null && goods.getStock() != 0) {
            goods.setReason(null); // 非售罄不需要下架原因
        }
    }
}
