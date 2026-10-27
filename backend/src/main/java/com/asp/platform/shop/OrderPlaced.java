package com.asp.platform.shop;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// Published when a customer places an order on the public page and left an e-mail address; listeners run after the
/// commit (the notification module mails the status link). The status token exists only here and in the customer's
/// response: the database keeps its hash. Never log it.
///
/// @param language   the language the customer used on the page (`pt-PT`, `en`, `pt-BR`), or null
/// @param totalCents what the customer pays at the counter, VAT included
public record OrderPlaced(UUID tenantId, int number, String name, String email, @Nullable String language,
        long totalCents, Instant holdUntil, String statusToken) {

    @Override
    public String toString() {
        return "OrderPlaced[tenantId=" + tenantId + ", number=" + number + "]";
    }
}
