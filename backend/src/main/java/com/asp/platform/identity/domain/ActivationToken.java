package com.asp.platform.identity.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shared.StatusTokens;

/// A one-time link that lets a new user (or an owner the platform admin re-invites) set their password.
/// Only the SHA-256 of the token is stored; the token itself exists only in the link handed out once.
///
/// @param tokenHash hex SHA-256 of the token as it appears in the link
/// @param usedAt    when the link was used; a used link never works again
public record ActivationToken(UUID id, UUID userId, String tokenHash, Instant expiresAt, @Nullable Instant usedAt,
        Instant createdAt) {

    public static final Duration TTL = Duration.ofHours(72);

    public static ActivationToken issue(UUID id, UUID userId, String rawToken, Instant now) {
        return new ActivationToken(id, userId, hash(rawToken), now.plus(TTL), null, now);
    }

    /// Hex SHA-256 of the token's UTF-8 bytes, as stored.
    public static String hash(String rawToken) {
        return StatusTokens.hash(rawToken);
    }

    /// Checks that the link may still be used.
    ///
    /// @throws ActivationInvalidException when it was used already
    /// @throws ActivationExpiredException when it is past its expiry
    public void requireUsable(Instant now) {
        if (usedAt != null) {
            throw new ActivationInvalidException();
        }
        if (!now.isBefore(expiresAt)) {
            throw new ActivationExpiredException();
        }
    }

    public ActivationToken used(Instant now) {
        return new ActivationToken(id, userId, tokenHash, expiresAt, now, createdAt);
    }
}
