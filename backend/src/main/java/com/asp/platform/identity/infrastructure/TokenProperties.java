package com.asp.platform.identity.infrastructure;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/// @param issuer value of the `iss` claim
/// @param ttl    how long an access token stays valid
@Validated
@ConfigurationProperties("app.identity.token")
record TokenProperties(@NotBlank String issuer, @NotNull Duration ttl) {
}
