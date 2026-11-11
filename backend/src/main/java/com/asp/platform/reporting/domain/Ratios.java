package com.asp.platform.reporting.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.jspecify.annotations.Nullable;

/// The rounding rules of the report figures. Hours, percentages and ratios have two decimals, money is whole cents,
/// everything rounds half up (away from zero on a tie), and a figure that cannot be computed because the divisor is
/// zero is null, never 0.
public final class Ratios {

    private static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int SCALE = 2;

    private Ratios() {
    }

    /// Hours with two decimals, from seconds (rounded once, on the sum).
    public static BigDecimal hours(long seconds) {
        return BigDecimal.valueOf(seconds).divide(SECONDS_PER_HOUR, SCALE, RoundingMode.HALF_UP);
    }

    /// `part × 100 ÷ whole`, or null when `whole` is 0.
    public static @Nullable BigDecimal percent(long part, long whole) {
        if (whole == 0) {
            return null;
        }
        return BigDecimal.valueOf(part).multiply(HUNDRED).divide(BigDecimal.valueOf(whole), SCALE, RoundingMode.HALF_UP);
    }

    /// `numerator ÷ denominator` (an efficiency), or null when the denominator is 0.
    public static @Nullable BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.signum() == 0) {
            return null;
        }
        return numerator.divide(denominator, SCALE, RoundingMode.HALF_UP);
    }

    /// Standard hours ÷ tracked hours, worked out from the tracked seconds (so the rounding happens once), or null
    /// when nothing was tracked. Zero standard hours with tracked time is 0.00, not null.
    public static @Nullable BigDecimal efficiency(BigDecimal standardHours, long trackedSeconds) {
        if (trackedSeconds == 0) {
            return null;
        }
        return standardHours.multiply(SECONDS_PER_HOUR).divide(BigDecimal.valueOf(trackedSeconds), SCALE,
                RoundingMode.HALF_UP);
    }

    /// The average of `sum` cents over `count` documents, rounded to whole cents, or null without any.
    public static @Nullable Long averageCents(long sum, long count) {
        if (count == 0) {
            return null;
        }
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP).longValueExact();
    }

    /// Hours per job from the total seconds, or null without jobs.
    public static @Nullable BigDecimal hoursPer(long seconds, long count) {
        if (count == 0) {
            return null;
        }
        return BigDecimal.valueOf(seconds).divide(SECONDS_PER_HOUR.multiply(BigDecimal.valueOf(count)), SCALE,
                RoundingMode.HALF_UP);
    }

    /// What the tracked time cost: `seconds × cents per hour ÷ 3600`, whole cents.
    public static long labourCostCents(long seconds, long centsPerHour) {
        return BigDecimal.valueOf(seconds).multiply(BigDecimal.valueOf(centsPerHour))
                .divide(SECONDS_PER_HOUR, 0, RoundingMode.HALF_UP).longValueExact();
    }
}
