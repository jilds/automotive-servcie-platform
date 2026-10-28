package com.asp.platform.workshop.infrastructure;

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

import com.asp.platform.shared.persistence.DomainEntity;
import com.asp.platform.workshop.domain.Quote;
import com.asp.platform.workshop.domain.QuoteChannel;
import com.asp.platform.workshop.domain.QuoteHeadline;
import com.asp.platform.workshop.domain.QuoteLine;
import com.asp.platform.workshop.domain.QuoteStatus;

/// `tenant_id` is filled by the database from the connection's workshop and checked by row-level security. The
/// lines, totals, validity and token hash are written once, when the quote is sent; only the status and the
/// decision change afterwards.
@Entity
@Table(name = "quote")
class QuoteEntity implements DomainEntity<Quote> {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private int number;

    @Column(name = "work_order_id", nullable = false, updatable = false)
    private UUID workOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuoteStatus status;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    @Column(name = "sent_by", nullable = false, updatable = false)
    private UUID sentBy;

    @Column(name = "valid_until", nullable = false, updatable = false)
    private Instant validUntil;

    @Column(name = "net_cents", nullable = false, updatable = false)
    private long netCents;

    @Column(name = "vat_cents", nullable = false, updatable = false)
    private long vatCents;

    /// Stored for reports; always net plus VAT.
    @Column(name = "total_cents", nullable = false, updatable = false)
    private long totalCents;

    @Column(name = "status_token_hash", nullable = false, updatable = false)
    private String statusTokenHash;

    @Column(name = "decided_at")
    private @Nullable Instant decidedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "decided_channel")
    private @Nullable QuoteChannel decidedChannel;

    @Column(name = "decided_by")
    private @Nullable UUID decidedBy;

    @Column(name = "decided_name")
    private @Nullable String decidedName;

    @Column(name = "decided_note")
    private @Nullable String decidedNote;

    /// Optimistic lock; null until first saved, so Spring Data persists new entities instead of merging them.
    @Version
    @Column(nullable = false)
    private @Nullable Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    /// Inserted with the quote, never changed or removed (no orphan removal; the app role may not delete them).
    @OneToMany(mappedBy = "quote", cascade = CascadeType.PERSIST)
    @OrderBy("position")
    private List<QuoteLineEntity> lines = new ArrayList<>();

    @SuppressWarnings("NullAway.Init")
    protected QuoteEntity() {
    }

    QuoteEntity(Quote quote) {
        this.id = quote.id();
        this.number = quote.number();
        this.workOrderId = quote.workOrderId();
        this.sentAt = quote.sentAt();
        this.sentBy = quote.sentBy();
        this.validUntil = quote.validUntil();
        this.netCents = quote.netCents();
        this.vatCents = quote.vatCents();
        this.totalCents = quote.totalCents();
        this.statusTokenHash = quote.statusTokenHash();
        this.createdAt = quote.sentAt();
        for (QuoteLine line : quote.lines()) {
            lines.add(new QuoteLineEntity(this, line));
        }
        apply(quote);
    }

    /// Copies what changes after sending: the status and the decision.
    @Override
    public void apply(Quote quote) {
        this.status = quote.status();
        this.decidedAt = quote.decidedAt();
        this.decidedChannel = quote.decidedChannel();
        this.decidedBy = quote.decidedBy();
        this.decidedName = quote.decidedName();
        this.decidedNote = quote.decidedNote();
    }

    @Override
    public Quote toDomain() {
        Long v = version;
        return new Quote(id, number, workOrderId, status, sentAt, sentBy, validUntil, statusTokenHash,
                lines.stream().map(QuoteLineEntity::toDomain).toList(), netCents, vatCents, decidedAt,
                decidedChannel, decidedBy, decidedName, decidedNote, v == null ? 0 : v);
    }

    /// Without touching the lines, so reading the board loads no line.
    QuoteHeadline headline() {
        return new QuoteHeadline(id, workOrderId, number, status, validUntil, decidedAt, decidedChannel);
    }
}
