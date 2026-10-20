package com.asp.shared.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param jwtSecret HMAC secret for signing tokens, at least 32 bytes
 * @param issuer    value of the iss claim
 * @param tokenTtl  how long an access token stays valid
 */
@ConfigurationProperties("asp.security")
public record SecurityProperties(String jwtSecret, String issuer, Duration tokenTtl) {
}
