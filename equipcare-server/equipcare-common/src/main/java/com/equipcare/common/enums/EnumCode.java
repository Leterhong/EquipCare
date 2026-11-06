package com.equipcare.common.enums;

/**
 * 业务枚举统一契约。所有落入数据库的枚举值必须实现本接口，
 * 保证"状态字段使用明确的枚举定义"。
 */
public interface EnumCode {

    /** 存入数据库的编码，使用英文大写常量名 */
    String getCode();

    /** 中文标签，仅用于展示 */
    String getLabel();

    /** 判断给定编码是否属于当前枚举 */
    static <E extends Enum<E> & EnumCode> boolean contains(Class<E> type, String code) {
        if (code == null) {
            return false;
        }
        for (E constant : type.getEnumConstants()) {
            if (constant.getCode().equals(code)) {
                return true;
            }
        }
        return false;
    }
}
