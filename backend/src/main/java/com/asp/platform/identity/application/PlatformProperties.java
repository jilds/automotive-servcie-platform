package com.asp.platform.identity.application;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// `app.platform.*`: how workshops reach the ASP team, and the first platform admin. Every value is optional; blank
/// counts as unset. Nothing here has a default, so no real-looking address or credential is ever baked in.
///
/// @param contactEmail           shown on the login page under "Want ASP for your workshop?"
/// @param contactPhone           shown next to the e-mail
/// @param bootstrapAdminEmail    creates the first platform admin at startup when no platform admin exists yet
/// @param bootstrapAdminPassword that admin's password, 12 to 72 characters; pass it as an environment variable
/// @param bootstrapAdminName     that admin's name; "Platform admin" when unset
@ConfigurationProperties("app.platform")
public record PlatformProperties(@Nullable String contactEmail, @Nullable String contactPhone,
        @Nullable String bootstrapAdminEmail, @Nullable String bootstrapAdminPassword,
        @Nullable String bootstrapAdminName) {

    public @Nullable String contactEmailOrNull() {
        return blankToNull(contactEmail);
    }

    public @Nullable String contactPhoneOrNull() {
        return blankToNull(contactPhone);
    }

    static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Override
    public String toString() {
        // Keep the bootstrap password out of logs and actuator output.
        return "PlatformProperties[contactEmail=" + contactEmail + ", contactPhone=" + contactPhone
                + ", bootstrapAdminEmail=" + bootstrapAdminEmail + "]";
    }
}
