package com.asp.platform.billing.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// Row-level security limits every query to the current workshop.
public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(UUID id);

    /// Reads the payment and locks its row until the transaction ends (3 s, then 409 `try-again`).
    Optional<Payment> lockById(UUID id);

    Optional<Payment> findByProviderPaymentId(String provider, String providerPaymentId);

    /// The payments of the invoice, oldest first.
    List<Payment> findByInvoice(UUID invoiceId);

    List<Payment> findByInvoices(Collection<UUID> invoiceIds);

    /// The payments of the invoice with their rows locked until the transaction ends (3 s, then 409 `try-again`),
    /// in id order. Lock the invoice row first.
    List<Payment> lockByInvoice(UUID invoiceId);

    /// Where a payment belongs, read without loading it (so the invoice can be locked before the payment: the
    /// lock order is always invoice, then its payments).
    record Ref(UUID id, UUID invoiceId) {
    }

    Optional<Ref> refOf(UUID paymentId);

    Optional<Ref> refOfProviderPayment(String provider, String providerPaymentId);

    /// Where the keyset of [#findDueForExpiry] stands: the last payment seen.
    record Cursor(Instant expiresAt, UUID id) {
    }

    /// PENDING payments whose validity ended before `now`, by (expires_at, id), after the cursor when given.
    List<Cursor> findDueForExpiry(Instant now, @org.jspecify.annotations.Nullable Cursor after, int limit);
}
