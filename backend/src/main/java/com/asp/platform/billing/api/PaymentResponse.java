package com.asp.platform.billing.api;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.billing.domain.Payment;
import com.asp.platform.billing.domain.PaymentMethod;
import com.asp.platform.billing.domain.PaymentStatus;

/// A payment as the screens read it (docs/api/release-4.md "Payment resource").
record PaymentResponse(UUID id, UUID invoiceId, PaymentMethod method, PaymentStatus status, long amountCents,
        @Nullable String provider, @Nullable MultibancoResponse multibanco, @Nullable MbWayResponse mbway,
        @Nullable Instant expiresAt, @Nullable Instant paidAt, @Nullable String failureCode, @Nullable String note,
        Instant createdAt, long version) {

    record MultibancoResponse(String entity, String reference) {
    }

    record MbWayResponse(String phoneMasked) {
    }

    static PaymentResponse of(Payment p) {
        String entity = p.mbEntity();
        String reference = p.mbReference();
        String phone = p.mbwayPhoneMasked();
        return new PaymentResponse(p.id(), p.invoiceId(), p.method(), p.status(), p.amountCents(), p.provider(),
                entity == null || reference == null ? null : new MultibancoResponse(entity, reference),
                phone == null ? null : new MbWayResponse(phone), p.expiresAt(), p.paidAt(), p.failureCode(), p.note(),
                p.createdAt(), p.version());
    }
}
