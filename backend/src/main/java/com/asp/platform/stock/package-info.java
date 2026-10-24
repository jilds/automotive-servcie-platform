/// Stock: the workshop's parts with the quantity on hand, an append-only ledger of stock movements, reservations of
/// parts for work orders, and purchase orders that bring parts in.
/// Public API: [StockReservations] with [StockItem] and [ReservationSource], used by the workshop module (part lines
/// from stock), by the catalogue (products in service packages) and, later, by the online shop. Uses the identity module through
/// [com.asp.platform.identity.WorkshopDirectory] (names of the people who moved stock). Never uses the modules that
/// use it.
@ApplicationModule(displayName = "Stock")
@NullMarked
package com.asp.platform.stock;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
