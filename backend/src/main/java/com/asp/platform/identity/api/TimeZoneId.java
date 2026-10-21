package com.asp.platform.identity.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import org.jspecify.annotations.Nullable;

import com.asp.platform.identity.domain.WorkshopSettings;

/// A time zone id `java.time.ZoneId` accepts, such as "Europe/Lisbon". Null is valid; pair with `@NotBlank`.
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TimeZoneId.Validator.class)
public @interface TimeZoneId {

    String message() default "Time zone must be an IANA id, such as Europe/Lisbon";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<TimeZoneId, String> {

        @Override
        public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
            return value == null || WorkshopSettings.isZoneId(value);
        }
    }
}
