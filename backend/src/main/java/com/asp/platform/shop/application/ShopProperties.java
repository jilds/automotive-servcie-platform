package com.asp.platform.shop.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/// `app.shop.*`.
///
/// @param maxOpenOrders how many online orders (PLACED or READY) one workshop may hold at once; a new order beyond
///                      it answers 409 `shop-busy`. Orders are paid only at the counter, so this keeps anonymous
///                      orders from reserving all the stock.
@Validated
@ConfigurationProperties("app.shop")
public record ShopProperties(@DefaultValue("50") @Min(1) @Max(10_000) int maxOpenOrders) {
}
