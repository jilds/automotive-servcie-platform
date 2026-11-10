package com.asp.platform.billing.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shared.StaleDataException;

/// An invoice or credit note: an immutable snapshot of what a work order came to and who it was addressed to, plus the
/// outcome of asking the provider to issue it. Only the provider result, the status and the closing data change.
public final class Invoice {

    /// The last failed attempt to reach the provider.
    public record Failure(String code, String message) {
    }

    private final UUID id;
    private final DocumentKind kind;
    private InvoiceStatus status;
    private final UUID workOrderId;
    private final int workOrderNumber;
    private final @Nullable UUID creditOfId;
    private final UUID requestId;
    private final @Nullable String plate;
    private final IssuerSnapshot issuer;
    private final CustomerSnapshot customer;
    private final List<InvoiceLine> lines;
    private final @Nullable String reason;
    private final String provider;
    private @Nullable IssuedDocument issued;
    private @Nullable Failure failure;
    private int attempts;
    private @Nullable Instant lastAttemptAt;
    private @Nullable Instant attemptStartedAt;
    private @Nullable Instant closedAt;
    private @Nullable UUID closedBy;
    private @Nullable String closedReason;
    private final UUID createdBy;
    private final Instant createdAt;
    private final long version;

    @SuppressWarnings("java:S107")
    public Invoice(UUID id, DocumentKind kind, InvoiceStatus status, UUID workOrderId, int workOrderNumber,
            @Nullable UUID creditOfId, UUID requestId, @Nullable String plate, IssuerSnapshot issuer,
            CustomerSnapshot customer, List<InvoiceLine> lines, @Nullable String reason, String provider,
            @Nullable IssuedDocument issued, @Nullable Failure failure, int attempts, @Nullable Instant lastAttemptAt,
            @Nullable Instant attemptStartedAt,
            @Nullable Instant closedAt, @Nullable UUID closedBy, @Nullable String closedReason, UUID createdBy,
            Instant createdAt, long version) {
        this.id = id;
        this.kind = kind;
        this.status = status;
        this.workOrderId = workOrderId;
        this.workOrderNumber = workOrderNumber;
        this.creditOfId = creditOfId;
        this.requestId = requestId;
        this.plate = plate;
        this.issuer = issuer;
        this.customer = customer;
        this.lines = new ArrayList<>(lines);
        this.reason = reason;
        this.provider = provider;
        this.issued = issued;
        this.failure = failure;
        this.attempts = attempts;
        this.lastAttemptAt = lastAttemptAt;
        this.attemptStartedAt = attemptStartedAt;
        this.closedAt = closedAt;
        this.closedBy = closedBy;
        this.closedReason = closedReason;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.version = version;
    }

    /// A new document, stored as PENDING before the provider is asked.
    public static Invoice open(UUID id, DocumentKind kind, UUID workOrderId, int workOrderNumber,
            @Nullable UUID creditOfId, UUID requestId, @Nullable String plate, IssuerSnapshot issuer,
            CustomerSnapshot customer, List<InvoiceLine> lines, @Nullable String reason, String provider,
            UUID createdBy, Instant now) {
        return new Invoice(id, kind, InvoiceStatus.PENDING, workOrderId, workOrderNumber, creditOfId, requestId,
                plate, issuer, customer, lines, reason, provider, null, null, 0, null, null, null, null, null, createdBy,
                now, 0);
    }

    /// A credit note that mirrors this invoice in full, with new line ids.
    public Invoice creditNote(UUID noteId, UUID noteRequestId, String noteReason, String noteProvider,
            Supplier<UUID> lineIds, UUID by, Instant now) {
        List<InvoiceLine> mirrored = lines.stream()
                .map(l -> new InvoiceLine(lineIds.get(), l.position(), l.kind(), l.description(), l.quantity(),
                        l.unitPriceCents(), l.discountPercent(), l.vatRate(), l.netCents(), l.vatCents(),
                        l.sourceLineId()))
                .toList();
        return open(noteId, DocumentKind.CREDIT_NOTE, workOrderId, workOrderNumber, id, noteRequestId, plate, issuer,
                customer, mirrored, noteReason, noteProvider, by, now);
    }

    /// What the provider is asked to issue.
    public DocumentDraft draft(DocumentDraft.@Nullable Credited credited) {
        return new DocumentDraft(id, kind, issuer, customer, lines(), totals(), plate, credited, reason);
    }

    /// The provider issued it. A document that is already ISSUED stays as it is. One that was DISCARDED while the
    /// provider was issuing it keeps the number and is flagged `issued-after-discard`: it stays DISCARDED (closed data
    /// never changes) and shows the number, so someone can void it at the provider.
    ///
    /// @return true when this call changed the document
    public boolean markIssued(IssuedDocument document, Instant now) {
        if (status == InvoiceStatus.ISSUED) {
            return false;
        }
        if (status == InvoiceStatus.DISCARDED) {
            this.issued = document;
            this.failure = new Failure(ISSUED_AFTER_DISCARD, "The provider issued the document while it was discarded");
            this.lastAttemptAt = now;
            this.attemptStartedAt = null;
            return true;
        }
        requireRetryable();
        this.issued = document;
        this.failure = null;
        this.status = InvoiceStatus.ISSUED;
        this.lastAttemptAt = now;
        this.attemptStartedAt = null;
        return true;
    }

    /// The code of the flag on a document the provider issued after it was discarded.
    public static final String ISSUED_AFTER_DISCARD = "issued-after-discard";

    /// The provider refused it: definitive until someone retries or discards it.
    public void markRejected(String code, String message, Instant now) {
        requireRetryable();
        this.failure = new Failure(code, message);
        this.status = InvoiceStatus.FAILED;
        this.lastAttemptAt = now;
        this.attemptStartedAt = null;
    }

    /// The provider did not answer: the outcome is unknown, so the document waits as PENDING.
    public void markUnavailable(String message, Instant now) {
        requireRetryable();
        this.failure = new Failure(ProviderUnavailableException.CODE, message);
        this.status = InvoiceStatus.PENDING;
        this.lastAttemptAt = now;
        this.attemptStartedAt = null;
    }

    /// A call to the provider starts now and counts as an attempt at once (so a call whose result is never stored still
    /// counts towards the retry limit); until its result is stored, a discard is refused (see [#attemptInFlight]).
    public void markAttemptStarted(Instant now) {
        requireRetryable();
        this.attemptStartedAt = now;
        this.attempts++;
    }

    /// True while a call that started less than `window` ago has not stored its result.
    public boolean attemptInFlight(Instant now, java.time.Duration window) {
        Instant started = attemptStartedAt;
        return started != null && started.plus(window).isAfter(now);
    }

    public @Nullable Instant attemptStartedAt() {
        return attemptStartedAt;
    }

    /// @throws InvoiceNotRetryableException unless PENDING or FAILED
    public void requireRetryable() {
        if (!status.awaitsProvider()) {
            throw new InvoiceNotRetryableException();
        }
    }

    /// A failed document is given up on; the work order (or invoice) is free for a new one.
    ///
    /// @throws InvoiceNotDiscardableException unless FAILED
    public void discard(String why, UUID by, Instant now) {
        if (status != InvoiceStatus.FAILED) {
            throw new InvoiceNotDiscardableException();
        }
        close(InvoiceStatus.DISCARDED, why, by, now);
    }

    /// @throws InvoiceNotVoidableException unless an ISSUED invoice
    public void requireVoidable() {
        if (kind != DocumentKind.INVOICE || status != InvoiceStatus.ISSUED) {
            throw new InvoiceNotVoidableException();
        }
    }

    public void voidIt(String why, UUID by, Instant now) {
        requireVoidable();
        close(InvoiceStatus.VOIDED, why, by, now);
    }

    private void close(InvoiceStatus target, String why, UUID by, Instant now) {
        this.status = target;
        this.closedAt = now;
        this.attemptStartedAt = null;
        this.closedBy = by;
        this.closedReason = why;
    }

    /// @throws StaleDataException when someone else saved the document since
    public void requireVersion(long expected) {
        StaleDataException.check(expected, version);
    }

    /// @throws InvoiceNotIssuedException unless an ISSUED invoice (not a credit note)
    public void requireIssuedInvoice() {
        if (kind != DocumentKind.INVOICE || status != InvoiceStatus.ISSUED) {
            throw new InvoiceNotIssuedException();
        }
    }

    public boolean isIssuedInvoice() {
        return kind == DocumentKind.INVOICE && status == InvoiceStatus.ISSUED;
    }

    public InvoiceTotals totals() {
        return InvoiceTotals.of(lines);
    }

    public long totalCents() {
        return totals().totalCents();
    }

    public UUID id() {
        return id;
    }

    public DocumentKind kind() {
        return kind;
    }

    public InvoiceStatus status() {
        return status;
    }

    public UUID workOrderId() {
        return workOrderId;
    }

    public int workOrderNumber() {
        return workOrderNumber;
    }

    public @Nullable UUID creditOfId() {
        return creditOfId;
    }

    public UUID requestId() {
        return requestId;
    }

    public @Nullable String plate() {
        return plate;
    }

    public IssuerSnapshot issuer() {
        return issuer;
    }

    public CustomerSnapshot customer() {
        return customer;
    }

    public List<InvoiceLine> lines() {
        return List.copyOf(lines);
    }

    public @Nullable String reason() {
        return reason;
    }

    public String provider() {
        return provider;
    }

    public @Nullable IssuedDocument issued() {
        return issued;
    }

    public @Nullable Failure failure() {
        return failure;
    }

    public int attempts() {
        return attempts;
    }

    public @Nullable Instant lastAttemptAt() {
        return lastAttemptAt;
    }

    public @Nullable Instant closedAt() {
        return closedAt;
    }

    public @Nullable UUID closedBy() {
        return closedBy;
    }

    public @Nullable String closedReason() {
        return closedReason;
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
