package com.asp.platform.billing.domain;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/// The totals of a document: sums of the line nets and line VATs (each line was rounded half up by the work order, so
/// the total equals what the customer saw) and the VAT summary per rate.
public record InvoiceTotals(long netCents, long vatCents, long totalCents, List<VatSummaryRow> vatSummary) {

    public static InvoiceTotals of(List<InvoiceLine> lines) {
        long net = 0;
        long vat = 0;
        Map<BigDecimal, long[]> perRate = new TreeMap<>(Comparator.naturalOrder());
        for (InvoiceLine line : lines) {
            net += line.netCents();
            vat += line.vatCents();
            long[] sums = perRate.computeIfAbsent(line.vatRate().stripTrailingZeros(), rate -> new long[2]);
            sums[0] += line.netCents();
            sums[1] += line.vatCents();
        }
        List<VatSummaryRow> summary = perRate.entrySet().stream()
                .map(e -> new VatSummaryRow(e.getKey().setScale(2), e.getValue()[0], e.getValue()[1])).toList();
        return new InvoiceTotals(net, vat, net + vat, summary);
    }
}
