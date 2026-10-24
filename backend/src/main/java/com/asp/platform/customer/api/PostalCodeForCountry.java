package com.asp.platform.customer.api;

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

import com.asp.platform.customer.domain.Address;

/// Class-level check on an address: a Portuguese one (country PT or not given) needs a postal code like 1100-053.
/// Reported on `postalCode`, so a customer's address error reads `address.postalCode`.
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PostalCodeForCountry.Validator.class)
@interface PostalCodeForCountry {

    String message() default "Portuguese postal codes look like 1100-053";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<PostalCodeForCountry, AddressRequest> {

        @Override
        public boolean isValid(@Nullable AddressRequest value, ConstraintValidatorContext context) {
            // A missing postal code is reported by @NotBlank on the field.
            if (value == null || value.postalCode() == null || value.postalCode().isBlank()
                    || Address.isValidPostalCode(value.country(), value.postalCode())) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("postalCode")
                    .addConstraintViolation();
            return false;
        }
    }
}
