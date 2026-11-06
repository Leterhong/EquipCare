package com.equipcare.asset.enums;

import com.equipcare.common.enums.EnumCode;

/**
 * 设备文件用途。
 *
 * <p>图片用于设备详情与打印页展示，首张图片作为封面；附件用于说明书、合格证等资料。
 */
public enum EquipmentFileType implements EnumCode {

    IMAGE("IMAGE", "设备图片"),
    ATTACHMENT("ATTACHMENT", "附件");

    private final String code;
    private final String label;

    EquipmentFileType(String code, String label) {
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

    /** 编码是否合法，避免把任意字符串写进库 */
    public static boolean isValid(String code) {
        for (EquipmentFileType type : values()) {
            if (type.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
