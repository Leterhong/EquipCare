package com.equipcare.inspection.enums;

import com.equipcare.common.enums.EnumCode;

/**
 * 单项巡检结果。
 *
 * <p>三项判定是第四阶段的核心：巡检人必须对每个检查项明确表态，
 * 而不是「没填就是正常」。
 * <ul>
 *   <li>NORMAL 正常：实测值在标准内；</li>
 *   <li>ABNORMAL 异常：超出标准或现场人工判定异常，必须填写原因；</li>
 *   <li>SKIPPED 跳过：设备停机、不具备检查条件等，必须填写原因，必检项不允许跳过。</li>
 * </ul>
 */
public enum InspItemResult implements EnumCode {

    NORMAL("NORMAL", "正常"),
    ABNORMAL("ABNORMAL", "异常"),
    SKIPPED("SKIPPED", "跳过");

    private final String code;
    private final String label;

    InspItemResult(String code, String label) {
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

    public static InspItemResult of(String code) {
        for (InspItemResult r : values()) {
            if (r.code.equals(code)) {
                return r;
            }
        }
        return null;
    }

    public static boolean isValid(String code) {
        return of(code) != null;
    }
}
