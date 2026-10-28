package com.asp.platform.workshop;

import java.time.Instant;
import java.util.UUID;

/// Published when the front desk sends a quote and the vehicle's customer has an e-mail; listeners run after the
/// commit (the notification module mails the approval link). The status token exists only here and in the send
/// response: the database keeps its hash. Never log it; [#toString] leaves it and the contact out.
///
/// @param quoteNumber     the quote's number in the workshop
/// @param workOrderNumber the work order's number in the workshop
/// @param plate           the plate as printed on the vehicle, or empty when the vehicle is not found
/// @param totalCents      what the quote comes to, VAT included
public record QuoteSent(UUID tenantId, int quoteNumber, int workOrderNumber, String name, String email, String plate,
        long totalCents, Instant validUntil, String statusToken) {

    @Override
    public String toString() {
        return "QuoteSent[tenantId=" + tenantId + ", quoteNumber=" + quoteNumber + "]";
    }
}
