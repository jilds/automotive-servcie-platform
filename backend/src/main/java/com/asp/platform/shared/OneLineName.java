package com.asp.platform.shared;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;

/// A name typed on a public page (shop order, quote approval): one line without links (`://`, `www.`), angle
/// brackets or control characters. Such a name reaches a mail or a staff screen, so a link or a line break in it
/// would turn the form into a phishing relay. Null passes; combine with `@NotBlank` and `@Size`.
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
@Pattern(regexp = OneLineName.REGEX)
@ReportAsSingleViolation
public @interface OneLineName {

    String REGEX = "(?is)(?!.*(://|www\\.|<|>|\\p{Cntrl}|[\\u0080-\\u009f\\u2028\\u2029])).*";

    String message() default "One line without links";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
