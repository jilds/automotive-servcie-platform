package com.asp.platform.workshop.domain;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

/// A labor or part line to add to a work order. Money is in integer cents.
public record NewLine(LineType type, String description, BigDecimal quantity, long unitPriceCents,
        @Nullable BigDecimal discountPercent, BigDecimal vatRate) {
}
