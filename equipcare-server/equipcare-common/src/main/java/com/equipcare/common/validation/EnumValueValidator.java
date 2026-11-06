package com.equipcare.common.validation;

import com.equipcare.common.enums.EnumCode;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link EnumValue} 的校验实现。空值交由 @NotBlank 处理，此处视为通过。
 */
public class EnumValueValidator implements ConstraintValidator<EnumValue, String> {

    private Class<? extends Enum<?>> enumType;

    @Override
    public void initialize(EnumValue annotation) {
        this.enumType = annotation.target();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        if (!EnumCode.class.isAssignableFrom(enumType)) {
            return false;
        }
        for (Enum<?> constant : enumType.getEnumConstants()) {
            if (((EnumCode) constant).getCode().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
