package com.asp.platform.catalogue.domain;

import org.jspecify.annotations.Nullable;

/// Trimming and length rules shared by the catalogue's texts.
final class Text {

    private Text() {
    }

    static String required(String value, String field, int max) {
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return checkLength(trimmed, field, max);
    }

    static @Nullable String optional(@Nullable String value, String field, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return checkLength(value.strip(), field, max);
    }

    private static String checkLength(String value, String field, int max) {
        if (value.length() > max) {
            throw new IllegalArgumentException(field + " must be at most " + max + " characters");
        }
        return value;
    }
}
