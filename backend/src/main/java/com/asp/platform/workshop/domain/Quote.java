package com.asp.platform.workshop.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shared.StaleDataException;

/// A frozen copy of a work order's lines and totals, sent to the customer for approval.
///
/// Status moves: SENT → APPROVED | REJECTED (decided once, by the customer on the link or by the staff for the
/// customer) | SUPERSEDED (the lines changed, a new quote was sent, or the order was cancelled) | EXPIRED (past
/// `validUntil`); nothing after that (`quote-not-open`). A SENT quote past `validUntil` reads EXPIRED at once
/// ([#statusAt]), before the hourly job stores it. The lines and totals never change after sending.
public final class Quote {

    public static final int MAX_NOTE = 500;
    public static final int MAX_NAME = 120;

    private final UUID id;
    private final int number;
    private final UUID workOrderId;
    private final Instant sentAt;
    private final UUID sentBy;
    private final Instant validUntil;
    private final String statusTokenHash;
    private final List<QuoteLine> lines;
    private final long netCents;
    private final long vatCents;
    private final long version;
    private QuoteStatus status;
    private @Nullable Instant decidedAt;
    private @Nullable QuoteChannel decidedChannel;
    private @Nullable UUID decidedBy;
    private @Nullable String decidedName;
    private @Nullable String decidedNote;

    /// A quote as stored.
    public Quote(UUID id, int number, UUID workOrderId, QuoteStatus status, Instant sentAt, UUID sentBy,
            Instant validUntil, String statusTokenHash, List<QuoteLine> lines, long netCents, long vatCents,
            @Nullable Instant decidedAt, @Nullable QuoteChannel decidedChannel, @Nullable UUID decidedBy,
            @Nullable String decidedName, @Nullable String decidedNote, long version) {
        this.id = id;
        this.number = number;
        this.workOrderId = workOrderId;
        this.status = status;
        this.sentAt = sentAt;
        this.sentBy = sentBy;
        this.validUntil = validUntil;
        this.statusTokenHash = statusTokenHash;
        this.lines = List.copyOf(lines);
        this.netCents = netCents;
        this.vatCents = vatCents;
        this.decidedAt = decidedAt;
        this.decidedChannel = decidedChannel;
        this.decidedBy = decidedBy;
        this.decidedName = decidedName;
        this.decidedNote = decidedNote;
        this.version = version;
    }

    /// A new SENT quote of the order's current lines and totals, open for `validDays` from `now`; not saved yet.
    ///
    /// @throws WorkOrderNotQuotableException outside DIAGNOSTICS / AWAITING_APPROVAL or without lines
    public static Quote send(UUID id, int number, WorkOrder order, UUID sentBy, String statusTokenHash, Instant now,
            int validDays, Supplier<UUID> lineIds) {
        order.requireQuotable();
        List<QuoteLine> lines = new ArrayList<>();
        List<WorkOrderLine> ordered = order.lines().stream()
                .sorted(Comparator.comparingInt(WorkOrderLine::position)).toList();
        for (int i = 0; i < ordered.size(); i++) {
            lines.add(QuoteLine.of(lineIds.get(), i + 1, ordered.get(i)));
        }
        return new Quote(id, number, order.id(), QuoteStatus.SENT, now, sentBy,
                now.plus(Duration.ofDays(validDays)), statusTokenHash, lines, order.netCents(), order.vatCents(),
                null, null, null, null, null, 0);
    }

    /// EXPIRED for a SENT quote past `validUntil`, else the stored status.
    public static QuoteStatus effectiveStatus(QuoteStatus stored, Instant validUntil, Instant now) {
        return stored == QuoteStatus.SENT && now.isAfter(validUntil) ? QuoteStatus.EXPIRED : stored;
    }

    /// What to show now: EXPIRED once a SENT quote is past `validUntil`.
    public QuoteStatus statusAt(Instant now) {
        return effectiveStatus(status, validUntil, now);
    }

    /// True while the customer or the staff may still approve or reject it.
    public boolean canDecide(Instant now) {
        return statusAt(now) == QuoteStatus.SENT;
    }

    /// True when the quote is stored as SENT but its validity has passed: the expiry job stores it as EXPIRED.
    public boolean isDue(Instant now) {
        return status == QuoteStatus.SENT && now.isAfter(validUntil);
    }

    /// @throws StaleDataException when someone saved the quote since the caller read it
    public void requireVersion(long expectedVersion) {
        StaleDataException.check(expectedVersion, version);
    }

    /// The customer approves on the link with the name they typed.
    public void approveByCustomer(String name, Instant now) {
        decide(QuoteStatus.APPROVED, QuoteChannel.CUSTOMER, null, requireText(name, MAX_NAME, "Name"), null, now);
    }

    /// The customer rejects on the link, with an optional reason.
    public void rejectByCustomer(@Nullable String reason, Instant now) {
        decide(QuoteStatus.REJECTED, QuoteChannel.CUSTOMER, null, null, optionalText(reason), now);
    }

    /// The front desk or owner records the customer's decision (given by phone or at the counter), with a note.
    ///
    /// @throws DecisionNoteRequiredException when the note is blank
    public void decideByStaff(QuoteDecision decision, @Nullable String note, UUID userId, Instant now) {
        String n = optionalText(note);
        if (n == null) {
            throw new DecisionNoteRequiredException();
        }
        QuoteStatus target = decision == QuoteDecision.APPROVED ? QuoteStatus.APPROVED : QuoteStatus.REJECTED;
        decide(target, QuoteChannel.STAFF, userId, null, n, now);
    }

    /// The order's lines changed, a new quote was sent, or the order was cancelled. A SENT quote already past its
    /// validity becomes EXPIRED instead, as it already reads.
    public void supersede(Instant now) {
        requireStoredSent();
        status = isDue(now) ? QuoteStatus.EXPIRED : QuoteStatus.SUPERSEDED;
    }

    /// SENT past `validUntil` → EXPIRED, by the hourly job or when someone tries to decide it.
    public void expire(Instant now) {
        if (!isDue(now)) {
            throw new QuoteNotOpenException();
        }
        status = QuoteStatus.EXPIRED;
    }

    /// True when the approved quote has a part typed by hand (no product), which probably still has to be ordered.
    public boolean hasPartsToOrder() {
        return lines.stream().anyMatch(QuoteLine::isPartToOrder);
    }

    /// One entry per VAT rate of the lines, ascending, with the sums of the line nets and line VATs.
    public List<VatTotal> vatByRate() {
        Map<BigDecimal, long[]> sums = new TreeMap<>();
        for (QuoteLine line : lines) {
            long[] sum = sums.computeIfAbsent(line.vatRate().stripTrailingZeros(), rate -> new long[2]);
            sum[0] += line.netCents();
            sum[1] += line.vatCents();
        }
        return sums.entrySet().stream()
                .map(e -> new VatTotal(e.getKey().setScale(2), e.getValue()[0], e.getValue()[1]))
                .toList();
    }

    private void decide(QuoteStatus target, QuoteChannel channel, @Nullable UUID by, @Nullable String name,
            @Nullable String note, Instant now) {
        requireStoredSent();
        if (isDue(now)) {
            throw new QuoteExpiredException();
        }
        status = target;
        decidedAt = now;
        decidedChannel = channel;
        decidedBy = by;
        decidedName = name;
        decidedNote = note;
    }

    private void requireStoredSent() {
        if (status != QuoteStatus.SENT) {
            throw new QuoteNotOpenException();
        }
    }

    private static @Nullable String optionalText(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return requireText(text, MAX_NOTE, "Note");
    }

    private static String requireText(String text, int max, String what) {
        String t = text.strip();
        if (t.isEmpty() || t.length() > max) {
            throw new IllegalArgumentException(what + " must have 1 to " + max + " characters");
        }
        return t;
    }

    public long totalCents() {
        return netCents + vatCents;
    }

    public UUID id() {
        return id;
    }

    public int number() {
        return number;
    }

    public UUID workOrderId() {
        return workOrderId;
    }

    public QuoteStatus status() {
        return status;
    }

    public Instant sentAt() {
        return sentAt;
    }

    public UUID sentBy() {
        return sentBy;
    }

    public Instant validUntil() {
        return validUntil;
    }

    public String statusTokenHash() {
        return statusTokenHash;
    }

    public List<QuoteLine> lines() {
        return lines;
    }

    public long netCents() {
        return netCents;
    }

    public long vatCents() {
        return vatCents;
    }

    public @Nullable Instant decidedAt() {
        return decidedAt;
    }

    public @Nullable QuoteChannel decidedChannel() {
        return decidedChannel;
    }

    public @Nullable UUID decidedBy() {
        return decidedBy;
    }

    public @Nullable String decidedName() {
        return decidedName;
    }

    public @Nullable String decidedNote() {
        return decidedNote;
    }

    public long version() {
        return version;
    }

    public QuoteHeadline headline() {
        return new QuoteHeadline(id, workOrderId, number, status, validUntil, decidedAt, decidedChannel);
    }
}
