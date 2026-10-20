package com.asp.platform.config.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/// @param jwtSecret HMAC secret that signs and verifies access tokens, at least 32 characters
@Validated
@ConfigurationProperties("app.security")
public record AppSecurityProperties(@NotBlank @Size(min = 32) String jwtSecret) {
}
