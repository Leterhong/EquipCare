package com.equipcare.inventory.enums;

import com.equipcare.common.enums.EnumCode;

/**
 * 库存流水类型。流水只允许插入，错误用反向流水冲正，不允许修改历史。
 */
public enum StockTxnType implements EnumCode {

    IN("IN", "入库"),
    OUT("OUT", "出库"),
    RETURN("RETURN", "退料入库"),
    ADJUST("ADJUST", "盘点调整");

    private final String code;
    private final String label;

    StockTxnType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
