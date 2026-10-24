package com.asp.platform.stock;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/// Public API of the stock module: reserve parts for a line of another module, give them back, or take them out of
/// stock. Every call joins the caller's transaction, so a failed reservation undoes the caller's change too.
///
/// The product row is locked while its stock is checked and changed, so two reservations of the last unit cannot
/// both succeed.
public interface StockReservations {

    /// The product to put on a line.
    ///
    /// @throws com.asp.platform.stock.domain.ProductNotFoundException when the workshop has no such product
    /// @throws com.asp.platform.stock.domain.ProductInactiveException when it is no longer sold
    StockItem item(UUID productId);

    /// Several products at once, in one query, to show them on another module's records (for example the products
    /// of a service package). Products no longer sold are included (`active` false); ids the workshop does not have
    /// are left out. An empty collection gives an empty map.
    Map<UUID, StockItem> items(Collection<UUID> productIds);

    /// Reserves `quantity` of the product for one line.
    ///
    /// @param sourceId     the order the line belongs to, used to release or consume the whole order
    /// @param sourceLineId the line, used to release just that line
    /// @throws com.asp.platform.stock.domain.InsufficientStockException when less than `quantity` is available
    /// @throws com.asp.platform.stock.domain.ProductInactiveException  when the product is no longer sold
    void reserve(UUID productId, BigDecimal quantity, ReservationSource source, UUID sourceId, UUID sourceLineId);

    /// Gives back what one line reserved. Does nothing when the line holds no active reservation.
    void releaseLine(ReservationSource source, UUID sourceLineId);

    /// Gives back everything an order reserved, for example when it is cancelled.
    void releaseAll(ReservationSource source, UUID sourceId);

    /// Takes everything an order reserved out of stock: one ISSUE movement per reservation, referencing the order.
    ///
    /// @param changedBy the user who delivered the order
    /// @throws com.asp.platform.stock.domain.InsufficientStockException when a count correction left less on hand
    ///                                                                 than was reserved
    void consumeAll(ReservationSource source, UUID sourceId, UUID changedBy);
}
