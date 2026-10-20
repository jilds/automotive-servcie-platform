package com.asp.platform.workshop.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.domain.LineType;
import com.asp.platform.workshop.domain.NewLine;

record LineRequest(
        @NotNull LineType type,
        @NotBlank String description,
        @NotNull @Positive BigDecimal quantity,
        @NotNull @PositiveOrZero Long unitPriceCents,
        @Nullable @DecimalMin("0") @DecimalMax("100") BigDecimal discountPercent,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal vatRate) {

    NewLine toNewLine() {
        return new NewLine(type, description, quantity, unitPriceCents, discountPercent, vatRate);
    }
}
