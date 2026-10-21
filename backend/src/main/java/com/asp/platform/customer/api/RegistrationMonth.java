package com.asp.platform.customer.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.YearMonth;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import org.jspecify.annotations.Nullable;

import com.asp.platform.customer.domain.VehicleRegistration;

/// A month of first registration: not before January 1937 and not in the future. Null is valid.
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RegistrationMonth.Validator.class)
@interface RegistrationMonth {

    String message() default "Registration month must be between 1937-01 and the current month";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<RegistrationMonth, YearMonth> {

        @Override
        public boolean isValid(@Nullable YearMonth value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            YearMonth current = YearMonth.now(context.getClockProvider().getClock());
            return !value.isBefore(VehicleRegistration.EARLIEST) && !value.isAfter(current);
        }
    }
}
