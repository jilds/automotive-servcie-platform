/// Service catalogue: the workshop's standard services (fixed price or priced by the hour, standard time) and
/// packages of services and stock products. Public API: [CatalogueLookup] and [CatalogueLine], used by the workshop
/// module to add catalogue lines to work orders. Uses identity's [com.asp.platform.identity.WorkshopDirectory] for the
/// labour rate and stock's [com.asp.platform.stock.StockReservations] to read the products of packages; never uses the
/// workshop module.
@ApplicationModule(displayName = "Catalogue")
@NullMarked
package com.asp.platform.catalogue;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
