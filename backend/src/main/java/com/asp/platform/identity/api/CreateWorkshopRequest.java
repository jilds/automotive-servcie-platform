package com.asp.platform.identity.api;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.asp.platform.identity.application.ProvisionWorkshopCommand;
import com.asp.platform.identity.domain.Plan;
import com.asp.platform.identity.domain.Subscription;
import com.asp.platform.identity.domain.SubscriptionStatus;
import com.asp.platform.shared.IsoCountry;
import com.asp.platform.shared.ValidNif;

/// @param status TRIAL or ACTIVE; ACTIVE when left out
/// @param slug   branded login address; derived from the name when left out
/// A Portuguese workshop (country PT) needs a valid NIF check digit.
@ValidNif
record CreateWorkshopRequest(
        @NotBlank @Size(max = 120) String name,
        @Nullable @Pattern(regexp = "\\d{9}", message = "NIF must have 9 digits") String nif,
        @NotBlank @IsoCountry String country,
        @NotBlank @TimeZoneId String timezone,
        @NotNull Plan plan,
        @Nullable SubscriptionStatus status,
        @Nullable LocalDate subscriptionUntil,
        @Nullable @Pattern(regexp = SLUG, message = SLUG_MESSAGE) String slug,
        @NotBlank @Size(max = 120) String ownerName,
        @NotBlank @Email @Size(max = 254) String ownerEmail) implements ValidNif.Subject {

    static final String SLUG = "[a-z0-9](?:[a-z0-9-]{0,38}[a-z0-9])?";
    static final String SLUG_MESSAGE = "Use 1 to 40 lower-case letters, digits and dashes";

    @Override
    public @Nullable String nifCountry() {
        return country;
    }

    ProvisionWorkshopCommand toCommand() {
        SubscriptionStatus initial = status == null ? SubscriptionStatus.ACTIVE : status;
        if (!initial.allowsSignIn()) {
            throw new IllegalArgumentException("A new workshop starts as TRIAL or ACTIVE");
        }
        return new ProvisionWorkshopCommand(name, nif, country, timezone,
                new Subscription(plan, initial, subscriptionUntil), slug == null || slug.isBlank() ? null : slug,
                ownerName, ownerEmail);
    }
}
