package com.asp.platform.booking.domain;

import org.jspecify.annotations.Nullable;

/// What the customer typed on the public form: a snapshot, never matched or merged with the customer records.
///
/// @param plate normalised like the vehicles' plates: upper case, no spaces or dashes ("AA00AA")
public record BookingContact(String name, @Nullable String phone, @Nullable String email, String plate,
        @Nullable String note) {

    public static final int MAX_NAME = 120;
    public static final int MAX_PHONE = 30;
    public static final int MAX_EMAIL = 254;
    public static final int MAX_PLATE = 20;
    public static final int MAX_NOTE = 500;

    public BookingContact {
        name = required(name, "Name", MAX_NAME);
        phone = optional(phone, "Phone", MAX_PHONE);
        email = optional(email, "E-mail", MAX_EMAIL);
        plate = required(plate, "Plate", MAX_PLATE);
        note = optional(note, "Note", MAX_NOTE);
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
