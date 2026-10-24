package com.asp.platform.catalogue.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.asp.platform.catalogue.domain.PackageDetails;
import com.asp.platform.catalogue.domain.PackageItem;
import com.asp.platform.catalogue.domain.ServiceDetails;

/// `POST /catalogue/packages`, and `PUT /catalogue/packages/{id}` which also needs `version`. Money in cents.
record PackageRequest(
        @NotBlank @Size(max = ServiceDetails.MAX_NAME) String name,
        @Nullable @Size(max = ServiceDetails.MAX_DESCRIPTION) String description,
        @NotNull @PositiveOrZero Long priceCents,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal vatRate,
        @Nullable Boolean active,
        @Nullable Boolean listedOnline,
        @NotEmpty @Size(max = 50) List<@Valid @NotNull Item> items,
        @NotNull(groups = OnUpdate.class) @Nullable Long version) {

    /// A service (`serviceId`) or a stock product (`productId`), exactly one of the two.
    record Item(@Nullable UUID serviceId, @Nullable UUID productId,
            @NotNull @Min(1) @Max(PackageItem.MAX_QUANTITY) Integer quantity) {

        /// Reported as `validation` with `errors['items[n].serviceOrProduct']`.
        @AssertTrue
        boolean isServiceOrProduct() {
            return (serviceId == null) != (productId == null);
        }

        PackageItem toDomain() {
            return new PackageItem(serviceId, productId, quantity);
        }
    }

    PackageDetails toDetails() {
        return new PackageDetails(name, description, priceCents, vatRate, !Boolean.FALSE.equals(active),
                Boolean.TRUE.equals(listedOnline), items.stream().map(Item::toDomain).toList());
    }

    long requiredVersion() {
        Long v = version;
        if (v == null) {
            throw new IllegalStateException("version is validated by the OnUpdate group");
        }
        return v;
    }
}
