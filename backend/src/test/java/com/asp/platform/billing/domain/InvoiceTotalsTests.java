package com.asp.platform.billing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class InvoiceTotalsTests {

    private static InvoiceLine line(int position, String vat, long net, long vatCents) {
        return new InvoiceLine(UUID.randomUUID(), position, LineKind.LABOR, "Linha " + position, BigDecimal.ONE, net,
                BigDecimal.ZERO, new BigDecimal(vat), net, vatCents, null);
    }

    @Test
    void should_add_the_line_amounts_so_the_total_is_net_plus_vat() {
        InvoiceTotals totals = InvoiceTotals.of(List.of(line(1, "23.00", 4000, 920), line(2, "23.00", 2250, 518)));

        assertThat(totals.netCents()).isEqualTo(6250);
        assertThat(totals.vatCents()).isEqualTo(1438);
        assertThat(totals.totalCents()).isEqualTo(7688);
    }

    @Test
    void should_summarise_per_vat_rate_in_ascending_order() {
        InvoiceTotals totals = InvoiceTotals.of(List.of(line(1, "23.00", 4000, 920), line(2, "6.00", 1000, 60),
                line(3, "0.00", 500, 0), line(4, "23.00", 2250, 518), line(5, "6", 333, 20)));

        assertThat(totals.vatSummary()).extracting(VatSummaryRow::vatRate, VatSummaryRow::netCents,
                VatSummaryRow::vatCents).containsExactly(
                        org.assertj.core.api.Assertions.tuple(new BigDecimal("0.00"), 500L, 0L),
                        org.assertj.core.api.Assertions.tuple(new BigDecimal("6.00"), 1333L, 80L),
                        org.assertj.core.api.Assertions.tuple(new BigDecimal("23.00"), 6250L, 1438L));
        assertThat(totals.vatSummary().stream().mapToLong(VatSummaryRow::netCents).sum()).isEqualTo(totals.netCents());
        assertThat(totals.vatSummary().stream().mapToLong(VatSummaryRow::vatCents).sum()).isEqualTo(totals.vatCents());
    }

    @Test
    void should_total_zero_for_no_lines() {
        InvoiceTotals totals = InvoiceTotals.of(List.of());

        assertThat(totals.totalCents()).isZero();
        assertThat(totals.vatSummary()).isEmpty();
    }
}
