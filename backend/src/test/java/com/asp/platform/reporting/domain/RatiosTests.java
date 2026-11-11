package com.asp.platform.reporting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/// Rounding and "cannot compute" rules of the report figures: half up, 2 decimals, null instead of dividing by zero.
class RatiosTests {

    @Test
    void should_turn_seconds_into_hours_with_two_decimals_half_up() {
        assertThat(Ratios.hours(0)).isEqualByComparingTo("0.00");
        assertThat(Ratios.hours(3600)).isEqualByComparingTo("1.00");
        assertThat(Ratios.hours(5400)).isEqualByComparingTo("1.50");
        // 18 s = 0.005 h rounds up to 0.01; 17 s rounds down.
        assertThat(Ratios.hours(1)).isEqualByComparingTo("0.00");
        assertThat(Ratios.hours(18)).isEqualByComparingTo("0.01");
        assertThat(Ratios.hours(17)).isEqualByComparingTo("0.00");
    }

    @Test
    void should_give_a_percentage_or_null_without_a_whole() {
        assertThat(Ratios.percent(3075, 4920)).isEqualByComparingTo("62.50");
        assertThat(Ratios.percent(1, 3)).isEqualByComparingTo("33.33");
        assertThat(Ratios.percent(2, 3)).isEqualByComparingTo("66.67");
        assertThat(Ratios.percent(5, 0)).isNull();
    }

    @Test
    void should_round_a_negative_percentage_half_away_from_zero() {
        assertThat(Ratios.percent(-5600, 6500)).isEqualByComparingTo("-86.15");
        assertThat(Ratios.percent(-1, 8)).isEqualByComparingTo("-12.50");
        // -1/800 = -0.125 %: a tie rounds away from zero.
        assertThat(Ratios.percent(-1, 800)).isEqualByComparingTo("-0.13");
    }

    @Test
    void should_divide_hours_into_an_efficiency_or_null_without_tracked_time() {
        assertThat(Ratios.ratio(new BigDecimal("3.50"), new BigDecimal("5.50"))).isEqualByComparingTo("0.64");
        assertThat(Ratios.ratio(new BigDecimal("0.00"), new BigDecimal("1.00"))).isEqualByComparingTo("0.00");
        assertThat(Ratios.ratio(new BigDecimal("2.00"), new BigDecimal("0.00"))).isNull();
        assertThat(Ratios.ratio(BigDecimal.ZERO, BigDecimal.ZERO)).isNull();
    }

    @Test
    void should_work_out_an_efficiency_from_tracked_seconds_or_null_without_any() {
        assertThat(Ratios.efficiency(new BigDecimal("3.50"), 19800)).isEqualByComparingTo("0.64");
        assertThat(Ratios.efficiency(new BigDecimal("3.50"), 23400)).isEqualByComparingTo("0.54");
        assertThat(Ratios.efficiency(new BigDecimal("2.00"), 3600)).isEqualByComparingTo("2.00");
        assertThat(Ratios.efficiency(BigDecimal.ZERO, 3600)).isEqualByComparingTo("0.00");
        assertThat(Ratios.efficiency(new BigDecimal("2.00"), 0)).isNull();
        assertThat(Ratios.efficiency(BigDecimal.ZERO, 0)).isNull();
    }

    @Test
    void should_average_cents_half_up_or_null_without_a_count() {
        assertThat(Ratios.averageCents(14145, 2)).isEqualTo(7073L);
        assertThat(Ratios.averageCents(11500, 2)).isEqualTo(5750L);
        assertThat(Ratios.averageCents(10, 4)).isEqualTo(3L);
        assertThat(Ratios.averageCents(100, 0)).isNull();
    }

    @Test
    void should_average_hours_per_job_from_seconds() {
        assertThat(Ratios.hoursPer(23400, 3)).isEqualByComparingTo("2.17");
        assertThat(Ratios.hoursPer(23400, 0)).isNull();
    }

    @Test
    void should_cost_labour_from_seconds_half_up_and_allow_a_zero_rate() {
        assertThat(Ratios.labourCostCents(12600, 3000)).isEqualTo(10500L);
        assertThat(Ratios.labourCostCents(1, 3000)).isEqualTo(1L);
        assertThat(Ratios.labourCostCents(0, 3000)).isEqualTo(0L);
        assertThat(Ratios.labourCostCents(7200, 0)).isEqualTo(0L);
        // 1 s at 1800 c/h = 0.5 c: a tie rounds up.
        assertThat(Ratios.labourCostCents(1, 1800)).isEqualTo(1L);
    }
}
