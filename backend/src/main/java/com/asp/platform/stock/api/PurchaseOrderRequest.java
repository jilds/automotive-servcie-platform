package com.asp.platform.stock.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.asp.platform.stock.domain.PurchaseOrderDetails;
import com.asp.platform.stock.domain.PurchaseOrderLine;
import com.asp.platform.stock.domain.Quantities;

/// `POST /stock/purchase-orders`, and `PUT /stock/purchase-orders/{id}` (drafts only) which also needs `version`.
/// Money in cents.
record PurchaseOrderRequest(
        @NotBlank @Size(max = PurchaseOrderDetails.MAX_SUPPLIER) String supplier,
        @Nullable @Size(max = PurchaseOrderDetails.MAX_NOTES) String notes,
        @NotEmpty @Size(max = PurchaseOrderDetails.MAX_LINES) List<@Valid @NotNull Line> lines,
        @NotNull(groups = OnUpdate.class) @Nullable Long version) {

    record Line(
            @NotNull UUID productId,
            @NotNull @Positive @Digits(integer = Quantities.INTEGER_DIGITS, fraction = Quantities.FRACTION_DIGITS)
            BigDecimal quantity,
            @NotNull @PositiveOrZero Long unitCostCents) {
    }

    /// Every line gets a new id: an edit replaces the lines of the draft.
    PurchaseOrderDetails toDetails() {
        return new PurchaseOrderDetails(supplier, notes, lines.stream()
                .map(l -> new PurchaseOrderLine(UUID.randomUUID(), l.productId(), l.quantity(), l.unitCostCents()))
                .toList());
    }

    long requiredVersion() {
        Long v = version;
        if (v == null) {
            throw new IllegalStateException("version is validated by the OnUpdate group");
        }
        return v;
    }
}
