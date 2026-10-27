package com.asp.platform.shop.domain;

import org.jspecify.annotations.Nullable;

/// Who placed the order, as typed on the public form: a snapshot, never matched with the customer records.
///
/// @param nif optional tax number for the invoice; checked by the caller when the workshop is in Portugal
public record ShopContact(String name, @Nullable String phone, @Nullable String email, @Nullable String nif) {

    public static final int MAX_NAME = 120;
    public static final int MAX_PHONE = 30;
    public static final int MAX_EMAIL = 254;
    public static final int MAX_NIF = 20;

    public ShopContact {
        name = required(name, "Name", MAX_NAME);
        phone = optional(phone, "Phone", MAX_PHONE);
        email = optional(email, "E-mail", MAX_EMAIL);
        nif = optional(nif, "NIF", MAX_NIF);
        if (phone == null && email == null) {
            throw new IllegalArgumentException("A phone or an e-mail is required");
        }
    }

    private static String required(String value, String label, int max) {
        String v = optional(value, label, max);
        if (v == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        return v;
    }

    private static @Nullable String optional(@Nullable String value, String label, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.strip();
        if (v.length() > max) {
            throw new IllegalArgumentException(label + " is longer than " + max + " characters");
        }
        return v;
    }
}
