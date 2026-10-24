package com.asp.platform.shared;

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

/// Class-level check of a request's NIF against its country: a Portuguese NIF must pass [PortugueseNif]. The error
/// is reported on the `nif` field. Other countries, and requests that do not say the country, are left to the domain.
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidNif.Validator.class)
public @interface ValidNif {

    String message() default "Not a valid Portuguese NIF";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /// A request that carries a NIF and the country it belongs to.
    interface Subject {

        @Nullable String nif();

        /// The NIF's country, or null when the request does not say it.
        @Nullable String nifCountry();
    }

    class Validator implements ConstraintValidator<ValidNif, Subject> {

        @Override
        public boolean isValid(@Nullable Subject value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            String country = value.nifCountry();
            if (country == null || PortugueseNif.isAcceptable(value.nif(), country)) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("nif")
                    .addConstraintViolation();
            return false;
        }
    }
}
