package com.equipcare.workorder.enums;

import com.equipcare.common.enums.EnumCode;

/**
 * SLA 考核状态。由系统定时任务计算，与业务状态互不覆盖。
 */
public enum SlaStatus implements EnumCode {

    NORMAL("NORMAL", "正常"),
    WARN("WARN", "预警"),
    OVERDUE("OVERDUE", "超时"),
    PAUSED("PAUSED", "挂起暂停");

    private final String code;
    private final String label;

    SlaStatus(String code, String label) {
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
