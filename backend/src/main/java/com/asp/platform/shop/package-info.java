/// Online shop with pay at the counter (add-on `shop`, docs/api/release-4.md): the stock products a workshop lists
/// online, the orders customers place on its public page (every line reserved in stock at once), the front desk's
/// preparation and hand-over (the customer pays at the counter, the stock is issued), the hold time after which an
/// order expires (`ShopOrderExpiry`, hourly) and the shop settings.
/// Public API: the events [OrderPlaced] and [OrderStatusChanged], which the notification module mails. Uses the
/// identity module through [com.asp.platform.identity.WorkshopDirectory] (time zone, open workshops, names of who
/// handed an order over) and the stock module through [com.asp.platform.stock.StockReservations] (products listed
/// online, reserve, release and consume). No other module depends on it except notification.
@ApplicationModule(displayName = "Shop")
@NullMarked
package com.asp.platform.shop;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
