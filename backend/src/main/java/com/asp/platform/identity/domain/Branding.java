package com.asp.platform.identity.domain;

import org.jspecify.annotations.Nullable;

/// How a workshop presents itself in the app and on its branded login page. Anything left unset falls back to the
/// ASP default theme (and to the workshop name for the display name).
///
/// @param displayName  name shown in the sidebar and on the login page, 1 to 80 characters
/// @param primaryColor buttons, links, focus ring
/// @param accentColor  active navigation marks and the brand mark
public record Branding(@Nullable String displayName, @Nullable BrandColor primaryColor,
        @Nullable BrandColor accentColor) {

    public static final int MAX_DISPLAY_NAME = 80;

    public Branding {
        displayName = displayName == null || displayName.isBlank() ? null : displayName.trim();
        if (displayName != null && displayName.length() > MAX_DISPLAY_NAME) {
            throw new IllegalArgumentException("Display name must be at most 80 characters");
        }
    }

    public static Branding none() {
        return new Branding(null, null, null);
    }
}
