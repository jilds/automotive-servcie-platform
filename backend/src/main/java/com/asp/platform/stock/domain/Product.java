package com.asp.platform.stock.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shared.StaleDataException;

/// A part the workshop keeps in stock, such as "Oil filter W 712/95". The quantity on hand changes only through
/// [#record], which returns the ledger row to store in the same transaction.
public final class Product {

    private final UUID id;
    private final long version;
    private ProductDetails details;
    private BigDecimal onHand;

    /// A new product with nothing on hand, not saved yet.
    public Product(UUID id, ProductDetails details) {
        this(id, details, BigDecimal.ZERO, 0);
    }

    /// @param version how many times the stored details have changed; edits name the version they read. Stock
    ///                movements do not change it, so an edit dialog is not made stale by a delivered work order.
    public Product(UUID id, ProductDetails details, BigDecimal onHand, long version) {
        this.id = id;
        this.details = details;
        this.onHand = Quantities.notNegative(onHand, "On hand");
        this.version = version;
    }

    public void update(ProductDetails newDetails) {
        this.details = newDetails;
    }

    /// @throws StaleDataException when someone else saved the product since the editor read it
    public void requireVersion(long expectedVersion) {
        StaleDataException.check(expectedVersion, version);
    }

    /// @throws ProductInactiveException when the product may no longer be reserved or ordered
    public void requireActive() {
        if (!details.active()) {
            throw new ProductInactiveException();
        }
    }

    /// Changes the quantity on hand and returns the ledger row that explains the change.
    ///
    /// @param quantity positive to add, negative to take away (its sign must suit the type)
    /// @throws InsufficientStockException        when it would leave less than nothing on hand
    /// @throws AdjustmentReasonRequiredException when an adjustment has no reason
    public StockMovement record(UUID movementId, MovementType type, BigDecimal quantity,
            @Nullable StockReference reference, @Nullable String reason, UUID changedBy, Instant at) {
        StockMovement movement = new StockMovement(movementId, id, type, quantity, reference, reason, changedBy, at);
        BigDecimal after = onHand.add(quantity);
        if (after.signum() < 0) {
            throw new InsufficientStockException();
        }
        this.onHand = Quantities.fits(after, "On hand");
        return movement;
    }

    public UUID id() {
        return id;
    }

    public long version() {
        return version;
    }

    public ProductDetails details() {
        return details;
    }

    public BigDecimal onHand() {
        return onHand;
    }
}
