package dev.xuya.demo.entity;

import dev.xuya.core.translate.DictEnum;

/**
 * 商品状态字典枚举：1 -> 在售，0 -> 售罄（免建字典表）
 */
public enum GoodsStatus implements DictEnum {

    ON_SALE(1, "在售"),
    SOLD_OUT(0, "售罄");

    private final Integer value;
    private final String label;

    GoodsStatus(Integer value, String label) {
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
