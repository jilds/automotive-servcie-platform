package com.asp.platform.billing.infrastructure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

import com.asp.platform.billing.domain.CustomerSnapshot;
import com.asp.platform.billing.domain.DocumentKind;
import com.asp.platform.billing.domain.FiscalAddress;
import com.asp.platform.billing.domain.Invoice;
import com.asp.platform.billing.domain.InvoiceLine;
import com.asp.platform.billing.domain.InvoiceStatus;
import com.asp.platform.billing.domain.InvoiceTotals;
import com.asp.platform.billing.domain.IssuedDocument;
import com.asp.platform.billing.domain.IssuerSnapshot;
import com.asp.platform.shared.persistence.DomainEntity;

/// `tenant_id` is filled by the database from the connection's workshop and checked by row-level security. The snapshot
/// (issuer, customer, totals, lines, reason) is written once, when the document is stored; afterwards only the provider
/// result, the status, the failure and closing data change (the column grants of V20 allow nothing else).
@Entity
@Table(name = "invoice")
class InvoiceEntity implements DomainEntity<Invoice> {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private DocumentKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status;

    @Column(name = "work_order_id", nullable = false, updatable = false)
    private UUID workOrderId;

    @Column(name = "work_order_number", nullable = false, updatable = false)
    private int workOrderNumber;

    @Column(name = "credit_of_id", updatable = false)
    private @Nullable UUID creditOfId;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(updatable = false)
    private @Nullable String plate;

    @Column(name = "issuer_name", nullable = false, updatable = false)
    private String issuerName;

    @Column(name = "issuer_nif", updatable = false)
    private @Nullable String issuerNif;

    @Column(name = "issuer_country", nullable = false, updatable = false)
    private String issuerCountry;

    @Column(name = "customer_name", nullable = false, updatable = false)
    private String customerName;

    @Column(name = "customer_nif", updatable = false)
    private @Nullable String customerNif;

    @Column(name = "customer_final_consumer", nullable = false, updatable = false)
    private boolean customerFinalConsumer;

    @Column(name = "customer_street", updatable = false)
    private @Nullable String customerStreet;

    @Column(name = "customer_detail", updatable = false)
    private @Nullable String customerDetail;

    @Column(name = "customer_postal_code", updatable = false)
    private @Nullable String customerPostalCode;

    @Column(name = "customer_locality", updatable = false)
    private @Nullable String customerLocality;

    @Column(name = "customer_country", updatable = false)
    private @Nullable String customerCountry;

    @Column(name = "net_cents", nullable = false, updatable = false)
    private long netCents;

    @Column(name = "vat_cents", nullable = false, updatable = false)
    private long vatCents;

    @Column(name = "total_cents", nullable = false, updatable = false)
    private long totalCents;

    @Column(updatable = false)
    private @Nullable String reason;

    @Column(nullable = false, updatable = false)
    private String provider;

    @Column(name = "provider_document_id")
    private @Nullable String providerDocumentId;

    private @Nullable String series;

    @Column(name = "sequence")
    private @Nullable Integer sequence;

    @Column(name = "full_number")
    private @Nullable String fullNumber;

    private @Nullable String atcud;

    @Column(name = "qr_payload")
    private @Nullable String qrPayload;

    @Column(name = "hash_fragment")
    private @Nullable String hashFragment;

    @Column(name = "issued_at")
    private @Nullable Instant issuedAt;

    @Column(name = "failure_code")
    private @Nullable String failureCode;

    @Column(name = "failure_message")
    private @Nullable String failureMessage;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_attempt_at")
    private @Nullable Instant lastAttemptAt;

    @Column(name = "attempt_started_at")
    private @Nullable Instant attemptStartedAt;

    @Column(name = "closed_at")
    private @Nullable Instant closedAt;

    @Column(name = "closed_by")
    private @Nullable UUID closedBy;

    @Column(name = "closed_reason")
    private @Nullable String closedReason;

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

    /// Inserted with the document, never changed or removed.
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.PERSIST)
    @OrderBy("position")
    private List<InvoiceLineEntity> lines = new ArrayList<>();

    @SuppressWarnings("NullAway.Init")
    protected InvoiceEntity() {
    }

    InvoiceEntity(Invoice invoice) {
        this.id = invoice.id();
        this.kind = invoice.kind();
        this.workOrderId = invoice.workOrderId();
        this.workOrderNumber = invoice.workOrderNumber();
        this.creditOfId = invoice.creditOfId();
        this.requestId = invoice.requestId();
        this.plate = invoice.plate();
        IssuerSnapshot issuer = invoice.issuer();
        this.issuerName = issuer.name();
        this.issuerNif = issuer.nif();
        this.issuerCountry = issuer.country();
        CustomerSnapshot customer = invoice.customer();
        this.customerName = customer.name();
        this.customerNif = customer.nif();
        this.customerFinalConsumer = customer.finalConsumer();
        FiscalAddress address = customer.address();
        if (address != null) {
            this.customerStreet = address.street();
            this.customerDetail = address.detail();
            this.customerPostalCode = address.postalCode();
            this.customerLocality = address.locality();
            this.customerCountry = address.country();
        }
        InvoiceTotals totals = invoice.totals();
        this.netCents = totals.netCents();
        this.vatCents = totals.vatCents();
        this.totalCents = totals.totalCents();
        this.reason = invoice.reason();
        this.provider = invoice.provider();
        this.createdBy = invoice.createdBy();
        this.createdAt = invoice.createdAt();
        for (InvoiceLine line : invoice.lines()) {
            lines.add(new InvoiceLineEntity(this, line));
        }
        apply(invoice);
    }

    /// Copies what changes after the document is stored.
    @Override
    public void apply(Invoice invoice) {
        this.status = invoice.status();
        IssuedDocument issued = invoice.issued();
        if (issued != null) {
            this.providerDocumentId = issued.providerDocumentId();
            this.series = issued.series();
            this.sequence = issued.sequence();
            this.fullNumber = issued.fullNumber();
            this.atcud = issued.atcud();
            this.qrPayload = issued.qrPayload();
            this.hashFragment = issued.hashFragment();
            this.issuedAt = issued.issuedAt();
        }
        Invoice.Failure failure = invoice.failure();
        this.failureCode = failure == null ? null : failure.code();
        this.failureMessage = failure == null ? null : failure.message();
        this.attempts = invoice.attempts();
        this.lastAttemptAt = invoice.lastAttemptAt();
        this.attemptStartedAt = invoice.attemptStartedAt();
        this.closedAt = invoice.closedAt();
        this.closedBy = invoice.closedBy();
        this.closedReason = invoice.closedReason();
    }

    @Override
    public Invoice toDomain() {
        Long v = version;
        FiscalAddress address = customerStreet == null ? null
                : new FiscalAddress(customerStreet, customerDetail, java.util.Objects.requireNonNull(customerPostalCode),
                        java.util.Objects.requireNonNull(customerLocality),
                        java.util.Objects.requireNonNull(customerCountry));
        IssuedDocument issued = fullNumber == null ? null
                : new IssuedDocument(java.util.Objects.requireNonNull(providerDocumentId),
                        java.util.Objects.requireNonNull(series), java.util.Objects.requireNonNull(sequence),
                        fullNumber, java.util.Objects.requireNonNull(atcud),
                        java.util.Objects.requireNonNull(qrPayload), java.util.Objects.requireNonNull(hashFragment),
                        java.util.Objects.requireNonNull(issuedAt), totalCents);
        Invoice.Failure failure = failureCode == null ? null
                : new Invoice.Failure(failureCode, java.util.Objects.toString(failureMessage, ""));
        return new Invoice(id, kind, status, workOrderId, workOrderNumber, creditOfId, requestId, plate,
                new IssuerSnapshot(issuerName, issuerNif, issuerCountry),
                new CustomerSnapshot(customerName, customerNif, customerFinalConsumer, address),
                lines.stream().map(InvoiceLineEntity::toDomain).toList(), reason, provider, issued, failure, attempts,
                lastAttemptAt, attemptStartedAt, closedAt, closedBy, closedReason, createdBy, createdAt, v == null ? 0 : v);
    }
}
