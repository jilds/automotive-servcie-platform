package com.asp.platform.billing.domain;

import java.math.BigDecimal;

/// The sums of the line nets and line VATs of one VAT rate.
public record VatSummaryRow(BigDecimal vatRate, long netCents, long vatCents) {
}
