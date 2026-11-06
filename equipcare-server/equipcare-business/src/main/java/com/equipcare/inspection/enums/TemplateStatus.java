package com.equipcare.inspection.enums;

import com.equipcare.common.enums.EnumCode;

/**
 * 巡检模板状态。
 *
 * <p>DRAFT 草稿可自由调整检查项；PUBLISHED 已发布可被计划引用（改动会升版本号）；
 * DISABLED 停用后不能再被新计划引用，但已在执行的计划不受影响。
 */
public enum TemplateStatus implements EnumCode {

    DRAFT("DRAFT", "草稿"),
    PUBLISHED("PUBLISHED", "已发布"),
    DISABLED("DISABLED", "已停用");

    private final String code;
    private final String label;

    TemplateStatus(String code, String label) {
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

    public static TemplateStatus of(String code) {
        for (TemplateStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        return null;
    }
}
