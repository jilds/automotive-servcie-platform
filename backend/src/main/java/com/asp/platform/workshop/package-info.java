/// Workshop floor: check-in, work orders with labor and parts, the Kanban board, service history, quotes with a
/// customer approval link and the owner dashboard.
/// Public API: the event [QuoteSent] (published after a quote is sent to a customer with an e-mail; carries the raw
/// link token, token-free `toString`), mailed by the notification module. It uses the catalogue module through [com.asp.platform.catalogue.CatalogueLookup]
/// (catalogue lines on work orders), the stock module through [com.asp.platform.stock.StockReservations] (part lines
/// from stock: reserve, release, consume on delivery), the customer module through [com.asp.platform.customer.CustomerManagement]
/// (including the customer's contact for the quote mail), the booking module through
/// [com.asp.platform.booking.BookingCheckIns] (check-in from a booking) and the identity module through
/// [com.asp.platform.identity.WorkshopDirectory] (owner dashboard, checking the assigned mechanic, the quote
/// validity).
@ApplicationModule(displayName = "Workshop")
@NullMarked
package com.asp.platform.workshop;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
