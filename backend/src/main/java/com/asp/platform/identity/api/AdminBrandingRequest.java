package com.asp.platform.identity.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.asp.platform.identity.domain.Branding;

/// The owner's theme fields plus the branded login address, which only the platform admin may change.
///
/// @param slug    the new slug, or null to keep it
/// @param version the workshop version the admin read; a different stored version answers 409 `stale-data`
record AdminBrandingRequest(
        @Nullable @Pattern(regexp = CreateWorkshopRequest.SLUG, message = CreateWorkshopRequest.SLUG_MESSAGE) String slug,
        @Nullable @Size(max = 80) String displayName,
        @Nullable @Pattern(regexp = BrandingRequest.COLOR, message = BrandingRequest.COLOR_MESSAGE) String primaryColor,
        @Nullable @Pattern(regexp = BrandingRequest.COLOR, message = BrandingRequest.COLOR_MESSAGE) String accentColor,
        @NotNull Long version) {

    Branding toBranding() {
        return BrandingRequest.toBranding(displayName, primaryColor, accentColor);
    }
}
