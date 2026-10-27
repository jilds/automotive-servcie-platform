package com.asp.platform.shop.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shared.StaleDataException;

/// An order placed on the workshop's public page, paid at the counter when collected.
///
/// Status moves: PLACED → READY | COLLECTED | CANCELLED | EXPIRED; READY → COLLECTED | CANCELLED | EXPIRED; nothing
/// after that (`shop-order-not-open`). The order holds stock while PLACED or READY; the caller reserves it when the
/// order is placed, releases it on a cancel or the expiry and consumes it on collection.
///
/// `holdUntil` is the placement plus the workshop's hold days, set again from the moment the order becomes READY.
public final class ShopOrder {

    public static final int MAX_LINES = 20;
    public static final int MAX_CANCEL_REASON = 500;

    private final UUID id;
    private final int number;
    private final ShopContact contact;
    private final @Nullable String language;
    private final boolean marketingConsent;
    private final Instant privacyAcceptedAt;
    private final String statusTokenHash;
    private final List<ShopOrderLine> lines;
    private final Instant placedAt;
    private final long version;
    private ShopOrderStatus status;
    private @Nullable Instant readyAt;
    private Instant holdUntil;
    private @Nullable PaymentMethod paymentMethod;
    private @Nullable UUID collectedBy;
    private @Nullable Instant collectedAt;
    private @Nullable CancelledBy cancelledBy;
    private @Nullable Instant cancelledAt;
    private @Nullable String cancelReason;

    /// An order as stored.
    ///
    /// @param language the page language the customer used (`pt-PT`, `en`, `pt-BR`), for the e-mails, or null
    public ShopOrder(UUID id, int number, ShopOrderStatus status, ShopContact contact, @Nullable String language,
            boolean marketingConsent, Instant privacyAcceptedAt, String statusTokenHash, List<ShopOrderLine> lines,
            Instant placedAt, @Nullable Instant readyAt, Instant holdUntil, @Nullable PaymentMethod paymentMethod,
            @Nullable UUID collectedBy, @Nullable Instant collectedAt, @Nullable CancelledBy cancelledBy,
            @Nullable Instant cancelledAt, @Nullable String cancelReason, long version) {
        requireLines(lines);
        this.id = id;
        this.number = number;
        this.status = status;
        this.contact = contact;
        this.language = language;
        this.marketingConsent = marketingConsent;
        this.privacyAcceptedAt = privacyAcceptedAt;
        this.statusTokenHash = statusTokenHash;
        this.lines = List.copyOf(lines);
        this.placedAt = placedAt;
        this.readyAt = readyAt;
        this.holdUntil = holdUntil;
        this.paymentMethod = paymentMethod;
        this.collectedBy = collectedBy;
        this.collectedAt = collectedAt;
        this.cancelledBy = cancelledBy;
        this.cancelledAt = cancelledAt;
        this.cancelReason = cancelReason;
        this.version = version;
    }

    /// A new order from the public page, not saved yet. The customer accepted the privacy notice `now`.
    public static ShopOrder place(UUID id, int number, ShopContact contact, @Nullable String language,
            boolean marketingConsent, String statusTokenHash, List<ShopOrderLine> lines, Instant now, int holdDays) {
        return new ShopOrder(id, number, ShopOrderStatus.PLACED, contact, language, marketingConsent, now,
                statusTokenHash, lines, now, null, hold(now, holdDays), null, null, null, null, null, null, 0);
    }

    /// 1 to [#MAX_LINES] lines, each product at most once.
    private static void requireLines(List<ShopOrderLine> lines) {
        if (lines.isEmpty() || lines.size() > MAX_LINES) {
            throw new IllegalArgumentException("An order has 1 to " + MAX_LINES + " lines");
        }
        Set<UUID> products = new HashSet<>();
        for (ShopOrderLine line : lines) {
            if (!products.add(line.productId())) {
                throw new IllegalArgumentException("A product is at most once on an order");
            }
        }
    }

    private static Instant hold(Instant from, int holdDays) {
        return from.plus(Duration.ofDays(holdDays));
    }

    /// @throws StaleDataException when someone else saved the order since the caller read it
    public void requireVersion(long expectedVersion) {
        StaleDataException.check(expectedVersion, version);
    }

    /// PLACED → READY; the hold starts again from now.
    public void markReady(Instant now, int holdDays) {
        require(status == ShopOrderStatus.PLACED);
        status = ShopOrderStatus.READY;
        readyAt = now;
        holdUntil = hold(now, holdDays);
    }

    /// PLACED or READY → COLLECTED: the customer paid at the counter and took the order.
    public void collect(PaymentMethod method, UUID userId, Instant now) {
        require(status.isOpen());
        status = ShopOrderStatus.COLLECTED;
        paymentMethod = method;
        collectedBy = userId;
        collectedAt = now;
    }

    /// PLACED or READY → CANCELLED by the front desk, with the reason the customer is told.
    ///
    /// @throws CancelReasonRequiredException when the reason is blank
    public void cancelByStaff(@Nullable String reason, Instant now) {
        if (reason == null || reason.isBlank()) {
            throw new CancelReasonRequiredException();
        }
        String r = reason.strip();
        if (r.length() > MAX_CANCEL_REASON) {
            throw new IllegalArgumentException("Reason is longer than " + MAX_CANCEL_REASON + " characters");
        }
        require(status.isOpen());
        close(ShopOrderStatus.CANCELLED, CancelledBy.STAFF, now);
        cancelReason = r;
    }

    /// PLACED → CANCELLED by the customer on the status link; once the order is READY only the workshop cancels.
    public void cancelByCustomer(Instant now) {
        require(canCancel());
        close(ShopOrderStatus.CANCELLED, CancelledBy.CUSTOMER, now);
    }

    /// True while the customer may cancel on the status link.
    public boolean canCancel() {
        return status == ShopOrderStatus.PLACED;
    }

    /// True when the order is still open after its hold time.
    public boolean isDue(Instant now) {
        return status.isOpen() && holdUntil.isBefore(now);
    }

    /// PLACED or READY → EXPIRED, by the hourly job once the hold time has passed.
    public void expire(Instant now) {
        require(isDue(now));
        close(ShopOrderStatus.EXPIRED, CancelledBy.EXPIRY, now);
    }

    private void close(ShopOrderStatus closed, CancelledBy by, Instant now) {
        status = closed;
        cancelledBy = by;
        cancelledAt = now;
    }

    private static void require(boolean allowed) {
        if (!allowed) {
            throw new ShopOrderNotOpenException();
        }
    }

    /// The sum of the line totals, VAT included.
    public long totalCents() {
        return lines.stream().mapToLong(ShopOrderLine::lineTotalCents).sum();
    }

    public UUID id() {
        return id;
    }

    public int number() {
        return number;
    }

    public ShopOrderStatus status() {
        return status;
    }

    public ShopContact contact() {
        return contact;
    }

    public @Nullable String language() {
        return language;
    }

    public boolean marketingConsent() {
        return marketingConsent;
    }

    public Instant privacyAcceptedAt() {
        return privacyAcceptedAt;
    }

    public String statusTokenHash() {
        return statusTokenHash;
    }

    public List<ShopOrderLine> lines() {
        return lines;
    }

    public Instant placedAt() {
        return placedAt;
    }

    public @Nullable Instant readyAt() {
        return readyAt;
    }

    public Instant holdUntil() {
        return holdUntil;
    }

    public @Nullable PaymentMethod paymentMethod() {
        return paymentMethod;
    }

    public @Nullable UUID collectedBy() {
        return collectedBy;
    }

    public @Nullable Instant collectedAt() {
        return collectedAt;
    }

    public @Nullable CancelledBy cancelledBy() {
        return cancelledBy;
    }

    public @Nullable Instant cancelledAt() {
        return cancelledAt;
    }

    public @Nullable String cancelReason() {
        return cancelReason;
    }

    public long version() {
        return version;
    }
}
