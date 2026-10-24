package com.asp.platform.stock.domain;

import java.math.BigDecimal;

/// Stock quantities: up to 9 digits before the decimal point and 3 after (numeric(12,3) in the database), so a
/// product can be counted in units, litres or metres.
public final class Quantities {

    public static final int INTEGER_DIGITS = 9;
    public static final int FRACTION_DIGITS = 3;
    private static final BigDecimal LIMIT = BigDecimal.TEN.pow(INTEGER_DIGITS);

    private Quantities() {
    }

    /// @throws IllegalArgumentException when the value is zero or negative, or does not fit
    public static BigDecimal positive(BigDecimal value, String field) {
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return fits(value, field);
    }

    /// @throws IllegalArgumentException when the value is negative or does not fit
    public static BigDecimal notNegative(BigDecimal value, String field) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException(field + " cannot be negative");
        }
        return fits(value, field);
    }

    /// @throws IllegalArgumentException when the value has more than 3 decimals or 9 digits before the point
    public static BigDecimal fits(BigDecimal value, String field) {
        if (value.stripTrailingZeros().scale() > FRACTION_DIGITS || value.abs().compareTo(LIMIT) >= 0) {
            throw new IllegalArgumentException(field + " must have at most " + INTEGER_DIGITS + " digits and "
                    + FRACTION_DIGITS + " decimals");
        }
        return value;
    }
}
