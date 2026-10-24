package com.asp.platform.catalogue.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.asp.platform.catalogue.domain.ServiceDetails;
import com.asp.platform.catalogue.domain.ServicePricing;

/// `POST /catalogue/services`, and `PUT /catalogue/services/{id}` which also needs `version`. Money in cents.
/// A missing `active` means active; a missing `listedOnline` means not listed online; a missing `pricing` means
/// `FIXED`. With `HOURLY` the price is the standard hours times the labour rate and `priceCents` is kept but not used
/// (send 0 or the last fixed price).
record ServiceRequest(
        @NotBlank @Size(max = ServiceDetails.MAX_NAME) String name,
        @Nullable @Size(max = ServiceDetails.MAX_DESCRIPTION) String description,
        @Nullable @Size(max = ServiceDetails.MAX_CATEGORY) String category,
        @NotNull @DecimalMin("0") @DecimalMax("999.99") @Digits(integer = 3, fraction = 2) BigDecimal standardHours,
        @Nullable ServicePricing pricing,
        @NotNull @PositiveOrZero Long priceCents,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal vatRate,
        @NotNull @Min(5) @Max(1440) Integer durationMinutes,
        @Nullable Boolean active,
        @Nullable Boolean listedOnline,
        @NotNull(groups = OnUpdate.class) @Nullable Long version) {

    ServiceDetails toDetails() {
        return new ServiceDetails(name, description, category, standardHours,
                pricing == null ? ServicePricing.FIXED : pricing, priceCents, vatRate, durationMinutes,
                !Boolean.FALSE.equals(active), Boolean.TRUE.equals(listedOnline));
    }

    long requiredVersion() {
        Long v = version;
        if (v == null) {
            throw new IllegalStateException("version is validated by the OnUpdate group");
        }
        return v;
    }
}
