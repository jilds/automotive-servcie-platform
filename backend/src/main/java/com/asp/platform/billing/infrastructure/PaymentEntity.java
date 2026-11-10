package com.asp.platform.billing.infrastructure;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

import com.asp.platform.billing.domain.Payment;
import com.asp.platform.billing.domain.PaymentMethod;
import com.asp.platform.billing.domain.PaymentStatus;
import com.asp.platform.shared.persistence.DomainEntity;

/// `tenant_id` comes from the connection. Amount, method, invoice and the provider data are written once; only the
/// status, the payment time and the failure code change (V21 column grants).
@Entity
@Table(name = "payment")
class PaymentEntity implements DomainEntity<Payment> {

    @Id
    private UUID id;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "amount_cents", nullable = false, updatable = false)
    private long amountCents;

    @Column(updatable = false)
    private @Nullable String provider;

    @Column(name = "provider_payment_id", updatable = false)
    private @Nullable String providerPaymentId;

    @Column(name = "mb_entity", updatable = false)
    private @Nullable String mbEntity;

    @Column(name = "mb_reference", updatable = false)
    private @Nullable String mbReference;

    @Column(name = "mbway_phone_masked", updatable = false)
    private @Nullable String mbwayPhoneMasked;

    @Column(name = "expires_at", updatable = false)
    private @Nullable Instant expiresAt;

    @Column(name = "paid_at")
    private @Nullable Instant paidAt;

    @Column(name = "failure_code")
    private @Nullable String failureCode;

    @Column(updatable = false)
    private @Nullable String note;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /// Optimistic lock; null until first saved, so Spring Data persists new entities instead of merging them.
    @Version
    @Column(nullable = false)
    private @Nullable Long version;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    @SuppressWarnings("NullAway.Init")
    protected PaymentEntity() {
    }

    PaymentEntity(Payment payment) {
        this.id = payment.id();
        this.invoiceId = payment.invoiceId();
        this.method = payment.method();
        this.amountCents = payment.amountCents();
        this.provider = payment.provider();
        this.providerPaymentId = payment.providerPaymentId();
        this.mbEntity = payment.mbEntity();
        this.mbReference = payment.mbReference();
        this.mbwayPhoneMasked = payment.mbwayPhoneMasked();
        this.expiresAt = payment.expiresAt();
        this.note = payment.note();
        this.createdBy = payment.createdBy();
        this.createdAt = payment.createdAt();
        apply(payment);
    }

    @Override
    public void apply(Payment payment) {
        this.status = payment.status();
        this.paidAt = payment.paidAt();
        this.failureCode = payment.failureCode();
    }

    @Override
    public Payment toDomain() {
        Long v = version;
        return new Payment(id, invoiceId, method, status, amountCents, provider, providerPaymentId, mbEntity,
                mbReference, mbwayPhoneMasked, expiresAt, paidAt, failureCode, note, createdBy, createdAt,
                v == null ? 0 : v);
    }
}
