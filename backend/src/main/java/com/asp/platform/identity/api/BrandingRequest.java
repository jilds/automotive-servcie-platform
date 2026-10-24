package com.asp.platform.identity.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.asp.platform.identity.domain.BrandColor;
import com.asp.platform.identity.domain.Branding;

/// The owner's theme settings. PUT replaces all three: null (or blank) falls back to the ASP default. `version` is the
/// workshop version the owner read; a different stored version answers 409 `stale-data`.
record BrandingRequest(
        @Nullable @Size(max = 80) String displayName,
        @Nullable @Pattern(regexp = COLOR, message = COLOR_MESSAGE) String primaryColor,
        @Nullable @Pattern(regexp = COLOR, message = COLOR_MESSAGE) String accentColor,
        @NotNull Long version) {

    static final String COLOR = "#[0-9a-fA-F]{6}";
    static final String COLOR_MESSAGE = "Colour must be #RRGGBB";

    Branding toBranding() {
        return toBranding(displayName, primaryColor, accentColor);
    }

    static Branding toBranding(@Nullable String displayName, @Nullable String primary, @Nullable String accent) {
        return new Branding(displayName, color(primary), color(accent));
    }

    private static @Nullable BrandColor color(@Nullable String hex) {
        return hex == null || hex.isBlank() ? null : new BrandColor(hex);
    }
}
