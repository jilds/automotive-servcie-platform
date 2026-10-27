package com.asp.platform.shop.application;

import java.time.ZoneId;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shop.domain.ShopOrder;

/// An order as its status link shows it, with the workshop's name, time zone and current pickup note.
public record PublicOrderView(ShopOrder order, String workshopName, ZoneId timeZone, @Nullable String pickupNote) {
}
