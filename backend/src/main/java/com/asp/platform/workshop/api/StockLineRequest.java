package com.asp.platform.workshop.api;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/// `POST /work-orders/{id}/lines/from-stock`: a part from stock, at its sale price. Quantities have at most two
/// decimals, like every work order line.
record StockLineRequest(
        @NotNull UUID productId,
        @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal quantity) {
}
