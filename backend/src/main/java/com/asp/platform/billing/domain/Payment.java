package com.asp.platform.billing.domain;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shared.StaleDataException;

/// Money paid, or promised through a Multibanco reference or an MB WAY request, towards one invoice.
public final class Payment {

    private final UUID id;
    private final UUID invoiceId;
    private final PaymentMethod method;
    private PaymentStatus status;
    private final long amountCents;
    private final @Nullable String provider;
    private final @Nullable String providerPaymentId;
    private final @Nullable String mbEntity;
    private final @Nullable String mbReference;
    private final @Nullable String mbwayPhoneMasked;
    private final @Nullable Instant expiresAt;
    private @Nullable Instant paidAt;
    private @Nullable String failureCode;
    private final @Nullable String note;
    private final UUID createdBy;
    private final Instant createdAt;
    private final long version;

    @SuppressWarnings("java:S107")
    public Payment(UUID id, UUID invoiceId, PaymentMethod method, PaymentStatus status, long amountCents,
            @Nullable String provider, @Nullable String providerPaymentId, @Nullable String mbEntity,
            @Nullable String mbReference, @Nullable String mbwayPhoneMasked, @Nullable Instant expiresAt,
            @Nullable Instant paidAt, @Nullable String failureCode, @Nullable String note, UUID createdBy,
            Instant createdAt, long version) {
        this.id = id;
        this.invoiceId = invoiceId;
        this.method = method;
        this.status = status;
        this.amountCents = amountCents;
        this.provider = provider;
        this.providerPaymentId = providerPaymentId;
        this.mbEntity = mbEntity;
        this.mbReference = mbReference;
        this.mbwayPhoneMasked = mbwayPhoneMasked;
        this.expiresAt = expiresAt;
        this.paidAt = paidAt;
        this.failureCode = failureCode;
        this.note = note;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.version = version;
    }

    /// Cash, card or transfer taken by staff: PAID at once.
    public static Payment recorded(UUID id, UUID invoiceId, PaymentMethod method, long amountCents,
            @Nullable String note, UUID by, Instant now) {
        if (method.viaProvider()) {
            throw new IllegalArgumentException("Not a staff method: " + method);
        }
        return new Payment(id, invoiceId, method, PaymentStatus.PAID, amountCents, null, null, null, null, null, null,
                now, null, note, by, now, 0);
    }

    /// A Multibanco reference the customer pays at an ATM or in home banking.
    public static Payment multibanco(UUID id, UUID invoiceId, long amountCents, String provider,
            String providerPaymentId, String entity, String reference, Instant expiresAt, @Nullable String note,
            UUID by, Instant now) {
        return new Payment(id, invoiceId, PaymentMethod.MULTIBANCO, PaymentStatus.PENDING, amountCents, provider,
                providerPaymentId, entity, reference, null, expiresAt, null, null, note, by, now, 0);
    }

    /// An MB WAY request the customer confirms on the phone.
    public static Payment mbway(UUID id, UUID invoiceId, long amountCents, String provider, String providerPaymentId,
            String phoneMasked, Instant expiresAt, @Nullable String note, UUID by, Instant now) {
        return new Payment(id, invoiceId, PaymentMethod.MBWAY, PaymentStatus.PENDING, amountCents, provider,
                providerPaymentId, null, null, phoneMasked, expiresAt, null, null, note, by, now, 0);
    }

    /// Applies what the provider says. Money that arrives late (the reference expired or was cancelled) still counts:
    /// it is real. A failed or paid payment does not change.
    ///
    /// @return true when the payment changed
    public boolean applyProviderState(ProviderPaymentState state, Instant now) {
        return switch (state) {
            case PAID -> {
                boolean open = status == PaymentStatus.PENDING || status == PaymentStatus.EXPIRED
                        || status == PaymentStatus.CANCELLED;
                if (open) {
                    status = PaymentStatus.PAID;
                    paidAt = now;
                    failureCode = null;
                }
                yield open;
            }
            case FAILED -> {
                boolean pending = status == PaymentStatus.PENDING;
                if (pending) {
                    status = PaymentStatus.FAILED;
                    failureCode = "provider-failed";
                }
                yield pending;
            }
            case EXPIRED -> {
                boolean pending = status == PaymentStatus.PENDING;
                if (pending) {
                    status = PaymentStatus.EXPIRED;
                }
                yield pending;
            }
            case PENDING -> false;
        };
    }

    /// @throws PaymentNotPendingException unless PENDING
    public void cancel() {
        requirePending();
        status = PaymentStatus.CANCELLED;
    }

    /// @throws PaymentNotPendingException unless PENDING
    public void requirePending() {
        if (status != PaymentStatus.PENDING) {
            throw new PaymentNotPendingException();
        }
    }

    /// @throws StaleDataException when someone else saved the payment since
    public void requireVersion(long expected) {
        StaleDataException.check(expected, version);
    }

    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    public UUID id() {
        return id;
    }

    public UUID invoiceId() {
        return invoiceId;
    }

    public PaymentMethod method() {
        return method;
    }

    public PaymentStatus status() {
        return status;
    }

    public long amountCents() {
        return amountCents;
    }

    public @Nullable String provider() {
        return provider;
    }

    public @Nullable String providerPaymentId() {
        return providerPaymentId;
    }

    public @Nullable String mbEntity() {
        return mbEntity;
    }

    public @Nullable String mbReference() {
        return mbReference;
    }

    public @Nullable String mbwayPhoneMasked() {
        return mbwayPhoneMasked;
    }

    public @Nullable Instant expiresAt() {
        return expiresAt;
    }

    public @Nullable Instant paidAt() {
        return paidAt;
    }

    public @Nullable String failureCode() {
        return failureCode;
    }

    public @Nullable String note() {
        return note;
    }

    public UUID createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public long version() {
        return version;
    }
}
