package com.asp.platform.identity.application;

import jakarta.validation.constraints.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/// `app.site.*`: the landing site on the main domain (step 03), as far as the app links to it.
///
/// @param privacyUrl the platform's privacy policy (`https://asp.example/privacidade`), linked from the public
///                   booking page; unset or blank, no link is shown. An absolute `https` URL; plain `http` only for
///                   `localhost` and `127.0.0.1` (a local site build), in every profile. Anything else, a relative
///                   path or a `javascript:` URL included, stops the start, so a typo never reaches the page
@Validated
@ConfigurationProperties("app.site")
public record SiteProperties(
        @Nullable @Pattern(regexp = "\\s*((https://[A-Za-z0-9.-]+|http://(localhost|127\\.0\\.0\\.1))(:\\d{1,5})?"
                + "(/[A-Za-z0-9._~%!$&+,;=:@/?#-]*)?)?\\s*",
                message = "absolute https URL (http only for localhost)") String privacyUrl) {

    public @Nullable String privacyUrlOrNull() {
        return PlatformProperties.blankToNull(privacyUrl);
    }
}
