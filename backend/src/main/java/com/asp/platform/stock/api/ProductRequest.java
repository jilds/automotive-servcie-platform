package com.asp.platform.stock.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.asp.platform.stock.domain.ProductDetails;
import com.asp.platform.stock.domain.Quantities;

/// `POST /stock/products`, and `PUT /stock/products/{id}` which also needs `version`. Money in cents. A missing
/// `unit` means "un" (units), a missing `minStock` 0, a missing `active` active and a missing `listedOnline` not
/// listed. The quantity on hand is not part of it: it changes through adjustments and purchase orders.
record ProductRequest(
        @NotBlank @Size(max = ProductDetails.MAX_SKU) String sku,
        @NotBlank @Size(max = ProductDetails.MAX_NAME) String name,
        @Nullable @Size(max = ProductDetails.MAX_BRAND) String brand,
        @Nullable @Size(max = ProductDetails.MAX_UNIT) String unit,
        @NotNull @PositiveOrZero Long costCents,
        @NotNull @PositiveOrZero Long salePriceCents,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal vatRate,
        @Nullable @DecimalMin("0") @Digits(integer = Quantities.INTEGER_DIGITS,
                fraction = Quantities.FRACTION_DIGITS) BigDecimal minStock,
        @Nullable @Size(max = ProductDetails.MAX_BIN) String bin,
        @Nullable Boolean active,
        @Nullable Boolean listedOnline,
        @NotNull(groups = OnUpdate.class) @Nullable Long version) {

    static final String DEFAULT_UNIT = "un";

    ProductDetails toDetails() {
        String u = unit;
        BigDecimal min = minStock;
        return new ProductDetails(sku, name, brand, u == null || u.isBlank() ? DEFAULT_UNIT : u, costCents,
                salePriceCents, vatRate, min == null ? BigDecimal.ZERO : min, bin, !Boolean.FALSE.equals(active),
                Boolean.TRUE.equals(listedOnline));
    }

    long requiredVersion() {
        Long v = version;
        if (v == null) {
            throw new IllegalStateException("version is validated by the OnUpdate group");
        }
        return v;
    }
}
