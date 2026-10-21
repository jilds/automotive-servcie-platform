package com.asp.platform.shared;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Locale;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import org.jspecify.annotations.Nullable;

/// An ISO 3166-1 alpha-2 country code, such as "PT", in any case. Null is valid; pair with `@NotBlank`
/// when the field is required.
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = IsoCountry.Validator.class)
public @interface IsoCountry {

    String message() default "Country must be an ISO 3166-1 alpha-2 code, such as PT";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<IsoCountry, String> {

        @Override
        public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
            return value == null || CountryCodes.isIso(value.trim().toUpperCase(Locale.ROOT));
        }
    }
}
