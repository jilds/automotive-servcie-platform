package com.asp.platform.booking.api;

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

/// Class-level check that a request gives a phone or an e-mail (or both), so the workshop can reach the customer.
/// Reported on both fields, `errors.phone` and `errors.email`, so the form can mark either.
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PhoneOrEmail.Validator.class)
@interface PhoneOrEmail {

    String message() default "Give a phone or an e-mail";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /// A request with a phone and an e-mail, either may be empty.
    interface Subject {

        @Nullable String phone();

        @Nullable String email();
    }

    class Validator implements ConstraintValidator<PhoneOrEmail, Subject> {

        @Override
        public boolean isValid(@Nullable Subject value, ConstraintValidatorContext context) {
            if (value == null || !blank(value.phone()) || !blank(value.email())) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            for (String field : new String[] {"phone", "email"}) {
                context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                        .addPropertyNode(field)
                        .addConstraintViolation();
            }
            return false;
        }

        private static boolean blank(@Nullable String s) {
            return s == null || s.isBlank();
        }
    }
}
