package com.asp.platform.config.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/// `app.public-site.*`: the landing site (step 03) on the main domain, which posts its demo request form to
/// `/api/v1/public-site/leads` on the app's domain.
///
/// @param origin     the site's origin, for example `https://asp.example`, the only one CORS lets post the form;
///                   unset, only the app's own origin can. Scheme, host and optional port only: `*` or a path stops
///                   the start, so a typo never opens the form to every site
/// @param leads      demo requests one client address may send: 5 an hour by default
/// @param leadsTotal demo requests from all addresses together: 50 an hour by default, so many addresses cannot
///                   fill the table or flood the platform admins' mailboxes
@Validated
@ConfigurationProperties("app.public-site")
public record PublicSiteProperties(
        @Nullable @Pattern(regexp = "\\s*(https?://[A-Za-z0-9.-]+(:\\d{1,5})?)?\\s*",
                message = "scheme://host[:port], no wildcard or path") String origin,
        @Valid @NotNull PublicRateLimitProperties.Rule leads,
        @Valid @NotNull PublicRateLimitProperties.Rule leadsTotal) {

    public @Nullable String originOrNull() {
        String o = origin;
        return o == null || o.isBlank() ? null : o.trim();
    }
}
