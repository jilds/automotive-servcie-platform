/// Workshop floor: check-in, work orders with labor and parts, the Kanban board, service history and the
/// owner dashboard.
/// Exposes no public API yet. It uses the catalogue module through [com.asp.platform.catalogue.CatalogueLookup]
/// (catalogue lines on work orders), the stock module through [com.asp.platform.stock.StockReservations] (part lines
/// from stock: reserve, release, consume on delivery), the customer module through [com.asp.platform.customer.CustomerManagement]
/// and the identity module through [com.asp.platform.identity.WorkshopDirectory] (owner dashboard, checking the
/// assigned mechanic).
@ApplicationModule(displayName = "Workshop")
@NullMarked
package com.asp.platform.workshop;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
