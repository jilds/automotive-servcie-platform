package com.asp.platform.billing.domain;

import org.jspecify.annotations.Nullable;

/// The customer as it was when the document was issued. Personal data: never logged.
///
/// @param nif           null for a customer without a tax number (and always for a final consumer)
/// @param finalConsumer true when the document was issued to "Consumidor final" on request
public record CustomerSnapshot(String name, @Nullable String nif, boolean finalConsumer,
        @Nullable FiscalAddress address) {

    public static final String FINAL_CONSUMER_NAME = "Consumidor final";

    @Override
    public String toString() {
        return "CustomerSnapshot[finalConsumer=" + finalConsumer + "]";
    }
}
