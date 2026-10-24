package com.asp.platform.stock.domain;

import java.math.BigDecimal;
import java.util.UUID;

import com.asp.platform.stock.ReservationSource;

/// Stock promised to one line of a work order (later a shop order). While it is active its quantity does not count
/// as available.
public final class Reservation {

    private final UUID id;
    private final UUID productId;
    private final BigDecimal quantity;
    private final ReservationSource source;
    private final UUID sourceId;
    private final UUID sourceLineId;
    private ReservationStatus status;

    public Reservation(UUID id, UUID productId, BigDecimal quantity, ReservationSource source, UUID sourceId,
            UUID sourceLineId, ReservationStatus status) {
        this.id = id;
        this.productId = productId;
        this.quantity = Quantities.positive(quantity, "Quantity");
        this.source = source;
        this.sourceId = sourceId;
        this.sourceLineId = sourceLineId;
        this.status = status;
    }

    /// Reserves `quantity` of an active product that has at least that much available.
    ///
    /// @param level the product's stock, read while its row is locked
    /// @throws ProductInactiveException   when the product is no longer sold
    /// @throws InsufficientStockException when less than `quantity` is available
    public static Reservation reserve(UUID id, Product product, StockLevel level, BigDecimal quantity,
            ReservationSource source, UUID sourceId, UUID sourceLineId) {
        product.requireActive();
        Quantities.positive(quantity, "Quantity");
        level.requireAvailable(quantity);
        return new Reservation(id, product.id(), quantity, source, sourceId, sourceLineId, ReservationStatus.ACTIVE);
    }

    /// Gives the quantity back.
    public void release() {
        requireActive();
        status = ReservationStatus.RELEASED;
    }

    /// The parts went out: the caller records the ISSUE movement on the product.
    public void consume() {
        requireActive();
        status = ReservationStatus.CONSUMED;
    }

    private void requireActive() {
        if (status != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("The reservation already ended: " + status);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID productId() {
        return productId;
    }

    public BigDecimal quantity() {
        return quantity;
    }

    public ReservationSource source() {
        return source;
    }

    public UUID sourceId() {
        return sourceId;
    }

    public UUID sourceLineId() {
        return sourceLineId;
    }

    public ReservationStatus status() {
        return status;
    }
}
