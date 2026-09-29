package dev.xuya.demo.entity;

import dev.xuya.core.translate.DictEnum;

/**
 * 商品类型（枚举字典演示：免建字典表直接翻译）
 */
public enum ProductType implements DictEnum {

    NORMAL(1, "普通商品"),
    GIFT(2, "赠品");

    private final int value;
    private final String label;

    ProductType(int value, String label) {
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
