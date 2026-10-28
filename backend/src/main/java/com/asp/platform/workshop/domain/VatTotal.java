package com.asp.platform.workshop.domain;

import java.math.BigDecimal;

/// The sums of the line nets and line VATs of one VAT rate.
public record VatTotal(BigDecimal vatRate, long netCents, long vatCents) {
}
