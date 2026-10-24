package com.asp.platform.identity.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;

/// A workshop's logo: a PNG, WEBP or SVG image of at most 512 KB.
///
/// The type is read from the bytes, never taken from the upload's declared content type. SVG files must pass
/// [SvgSafety], which refuses anything that could run script or load something from elsewhere.
public final class Logo {

    public static final int MAX_BYTES = 512 * 1024;
    public static final String PNG = "image/png";
    public static final String WEBP = "image/webp";
    public static final String SVG = "image/svg+xml";

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

    private final byte[] content;
    private final String contentType;
    private final String sha256;
    private final Instant updatedAt;

    private Logo(byte[] content, String contentType, String sha256, Instant updatedAt) {
        this.content = content;
        this.contentType = contentType;
        this.sha256 = sha256;
        this.updatedAt = updatedAt;
    }

    /// Checks an upload and turns it into a logo.
    ///
    /// @throws InvalidLogoException when it is empty, too large, not a supported image or an unsafe SVG
    public static Logo upload(byte[] content, Instant now) {
        if (content.length > MAX_BYTES) {
            throw new InvalidLogoException(InvalidLogoException.TOO_LARGE, "The logo must be at most 512 KB");
        }
        String type = detectType(content);
        if (SVG.equals(type)) {
            SvgSafety.check(content);
        }
        byte[] copy = content.clone();
        return new Logo(copy, type, sha256(copy), now);
    }

    /// A logo as stored, already checked when it was uploaded.
    public static Logo stored(byte[] content, String contentType, String sha256, Instant updatedAt) {
        return new Logo(content.clone(), contentType, sha256, updatedAt);
    }

    public byte[] content() {
        return content.clone();
    }

    public String contentType() {
        return contentType;
    }

    /// Hex SHA-256 of the content; also the HTTP ETag.
    public String sha256() {
        return sha256;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private static String detectType(byte[] content) {
        if (content.length >= PNG_SIGNATURE.length
                && Arrays.equals(content, 0, PNG_SIGNATURE.length, PNG_SIGNATURE, 0, PNG_SIGNATURE.length)) {
            return PNG;
        }
        if (content.length >= 12 && ascii(content, 0, 4).equals("RIFF") && ascii(content, 8, 4).equals("WEBP")) {
            return WEBP;
        }
        if (looksLikeXml(content)) {
            return SVG;
        }
        throw new InvalidLogoException(InvalidLogoException.UNSUPPORTED, "The logo must be a PNG, SVG or WEBP image");
    }

    private static boolean looksLikeXml(byte[] content) {
        String start = new String(content, 0, Math.min(content.length, 256), StandardCharsets.UTF_8)
                .replace("﻿", "").stripLeading().toLowerCase(Locale.ROOT);
        return start.startsWith("<");
    }

    private static String ascii(byte[] content, int offset, int length) {
        return new String(content, offset, length, StandardCharsets.US_ASCII);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every JDK", e);
        }
    }
}
