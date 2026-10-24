package com.asp.platform.stock.domain;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shared.StaleDataException;

/// An order of parts from a supplier. It can be changed while it is a draft; once received, its lines have been
/// added to stock with RECEIPT movements.
public final class PurchaseOrder {

    private final UUID id;
    private final int number;
    private final long version;
    private PurchaseOrderDetails details;
    private PurchaseOrderStatus status;
    private @Nullable Instant orderedAt;
    private @Nullable Instant receivedAt;

    public PurchaseOrder(UUID id, int number, PurchaseOrderDetails details, PurchaseOrderStatus status,
            @Nullable Instant orderedAt, @Nullable Instant receivedAt, long version) {
        this.id = id;
        this.number = number;
        this.details = details;
        this.status = status;
        this.orderedAt = orderedAt;
        this.receivedAt = receivedAt;
        this.version = version;
    }

    /// A new draft, not saved yet.
    public static PurchaseOrder draft(UUID id, int number, PurchaseOrderDetails details) {
        return new PurchaseOrder(id, number, details, PurchaseOrderStatus.DRAFT, null, null, 0);
    }

    /// @throws PurchaseOrderLockedException when the order was already sent to the supplier
    public void update(PurchaseOrderDetails newDetails) {
        if (status != PurchaseOrderStatus.DRAFT) {
            throw new PurchaseOrderLockedException();
        }
        this.details = newDetails;
    }

    /// The draft goes to the supplier.
    ///
    /// @throws IllegalPurchaseOrderStatusException when it is not a draft
    public void markOrdered(Instant now) {
        requireStatus(PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.ORDERED);
        status = PurchaseOrderStatus.ORDERED;
        orderedAt = now;
    }

    /// The parts arrived. The caller adds every line to stock in the same transaction.
    ///
    /// @throws IllegalPurchaseOrderStatusException when it was not ordered, or was already received
    public void markReceived(Instant now) {
        requireStatus(PurchaseOrderStatus.ORDERED, PurchaseOrderStatus.RECEIVED);
        status = PurchaseOrderStatus.RECEIVED;
        receivedAt = now;
    }

    /// @throws StaleDataException when someone else saved the order since the editor read it
    public void requireVersion(long expectedVersion) {
        StaleDataException.check(expectedVersion, version);
    }

    public long totalCents() {
        return details.lines().stream().mapToLong(PurchaseOrderLine::totalCents).sum();
    }

    private void requireStatus(PurchaseOrderStatus expected, PurchaseOrderStatus target) {
        if (status != expected) {
            throw new IllegalPurchaseOrderStatusException(status, target);
        }
    }

    public UUID id() {
        return id;
    }

    public int number() {
        return number;
    }

    public long version() {
        return version;
    }

    public PurchaseOrderDetails details() {
        return details;
    }

    public PurchaseOrderStatus status() {
        return status;
    }

    public @Nullable Instant orderedAt() {
        return orderedAt;
    }

    public @Nullable Instant receivedAt() {
        return receivedAt;
    }
}
