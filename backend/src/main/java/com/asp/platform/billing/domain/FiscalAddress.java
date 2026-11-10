package com.asp.platform.billing.domain;

import org.jspecify.annotations.Nullable;

/// A postal address as printed on an invoice.
public record FiscalAddress(String street, @Nullable String detail, String postalCode, String locality,
        String country) {
}
