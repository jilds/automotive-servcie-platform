package com.asp.platform.shop.application;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shop.domain.ShopOrder;

/// An order for the front desk, with the name of who handed it over (null until collected).
public record ShopOrderView(ShopOrder order, @Nullable String collectedByName) {
}
