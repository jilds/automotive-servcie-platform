package com.asp.platform.reporting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.asp.platform.shared.InvalidInputException;

/// Local dates in the workshop's zone, both ends inclusive; at most 24 months (plan 08).
class ReportPeriodTests {

    private static final ZoneId LISBON = ZoneId.of("Europe/Lisbon");
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

    @Test
    void should_default_to_the_month_of_today() {
        ReportPeriod period = ReportPeriod.of(null, null, LISBON, TODAY);

        assertThat(period.from()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(period.to()).isEqualTo(TODAY);
    }

    @Test
    void should_default_from_to_the_first_day_of_the_month_of_to() {
        ReportPeriod period = ReportPeriod.of(null, LocalDate.of(2026, 8, 20), LISBON, TODAY);

        assertThat(period.from()).isEqualTo(LocalDate.of(2026, 8, 1));
    }

    @Test
    void should_default_to_to_today_when_only_from_is_given() {
        ReportPeriod period = ReportPeriod.of(LocalDate.of(2026, 10, 1), null, LISBON, TODAY);

        assertThat(period.to()).isEqualTo(TODAY);
    }

    @Test
    void should_include_the_whole_last_day() {
        ReportPeriod period = ReportPeriod.of(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), LISBON, TODAY);

        // Lisbon is on UTC in November.
        assertThat(period.start()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
        assertThat(period.endExclusive()).isEqualTo(Instant.parse("2026-12-01T00:00:00Z"));
    }

    @Test
    void should_allow_a_single_day() {
        ReportPeriod period = ReportPeriod.of(TODAY, TODAY, LISBON, TODAY);

        assertThat(period.endExclusive()).isEqualTo(Instant.parse("2026-11-19T00:00:00Z"));
    }

    @Test
    void should_cut_days_by_the_zone_rules_across_the_spring_clock_change_in_lisbon() {
        // 2026-03-29: clocks go forward at 01:00 UTC, so the 29th starts at 00:00Z and ends at 23:00Z (a 23 hour day).
        ReportPeriod period = ReportPeriod.of(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 3, 29), LISBON, TODAY);

        assertThat(period.start()).isEqualTo(Instant.parse("2026-03-29T00:00:00Z"));
        assertThat(period.endExclusive()).isEqualTo(Instant.parse("2026-03-29T23:00:00Z"));
    }

    @Test
    void should_cut_days_by_the_zone_rules_across_the_autumn_clock_change_in_lisbon() {
        // 2026-10-25: clocks go back at 01:00 UTC: a 25 hour day that starts at 23:00Z on the 24th.
        ReportPeriod period = ReportPeriod.of(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 25), LISBON, TODAY);

        assertThat(period.start()).isEqualTo(Instant.parse("2026-10-24T23:00:00Z"));
        assertThat(period.endExclusive()).isEqualTo(Instant.parse("2026-10-26T00:00:00Z"));
    }

    @Test
    void should_use_the_offset_of_sao_paulo() {
        ReportPeriod period = ReportPeriod.of(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 10), SAO_PAULO, TODAY);

        assertThat(period.start()).isEqualTo(Instant.parse("2026-06-10T03:00:00Z"));
        assertThat(period.endExclusive()).isEqualTo(Instant.parse("2026-06-11T03:00:00Z"));
    }

    @Test
    void should_allow_exactly_24_months() {
        ReportPeriod period = ReportPeriod.of(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 12, 31), LISBON, TODAY);

        assertThat(period.from()).isEqualTo(LocalDate.of(2025, 1, 1));
    }

    @Test
    void should_refuse_more_than_24_months_with_its_own_code_on_to() {
        assertThatThrownBy(() -> ReportPeriod.of(LocalDate.of(2025, 1, 1), LocalDate.of(2027, 1, 1), LISBON, TODAY))
                .isInstanceOfSatisfying(InvalidInputException.class, e -> {
                    assertThat(e.code()).isEqualTo("report-range-too-large");
                    assertThat(e.field()).isEqualTo("to");
                });
    }

    @Test
    void should_refuse_a_start_after_the_end_as_a_validation_error_on_from() {
        assertThatThrownBy(() -> ReportPeriod.of(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 9), LISBON, TODAY))
                .isInstanceOfSatisfying(InvalidInputException.class, e -> {
                    assertThat(e.code()).isEqualTo("validation");
                    assertThat(e.field()).isEqualTo("from");
                });
    }
}
