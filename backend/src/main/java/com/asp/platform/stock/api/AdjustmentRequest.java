package com.asp.platform.stock.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.asp.platform.stock.domain.Quantities;
import com.asp.platform.stock.domain.StockMovement;

/// `POST /stock/products/{id}/adjustments`: add (positive) or take away (negative) a quantity, with a reason. A
/// blank reason answers 400 `adjustment-reason-required` on `reason`.
record AdjustmentRequest(
        @NotNull @Digits(integer = Quantities.INTEGER_DIGITS, fraction = Quantities.FRACTION_DIGITS)
        BigDecimal quantity,
        @Nullable @Size(max = StockMovement.MAX_REASON) String reason) {

    /// Reported as `errors.change` when the quantity is zero.
    @AssertTrue(message = "An adjustment changes the quantity")
    boolean isChange() {
        return quantity == null || quantity.signum() != 0;
    }

    String reasonOrBlank() {
        String r = reason;
        return r == null ? "" : r;
    }
}
