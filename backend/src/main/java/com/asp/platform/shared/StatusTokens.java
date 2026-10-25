package com.asp.platform.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/// Secret tokens handed out once in a link (activation links, public booking and order status links) and stored only
/// as their SHA-256, so a copy of the database opens no link.
public final class StatusTokens {

    /// 256 bits of randomness.
    public static final int BYTES = 32;
    /// Characters of a token: 32 bytes in base64url without padding.
    public static final int LENGTH = 43;

    private static final SecureRandom RANDOM = new SecureRandom();

    private StatusTokens() {
    }

    /// A new random token, URL-safe (base64url, no padding, [#LENGTH] characters).
    public static String generate() {
        byte[] bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /// Hex SHA-256 of the token's UTF-8 bytes (64 lower-case characters), as stored.
    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every JDK", e);
        }
    }
}
