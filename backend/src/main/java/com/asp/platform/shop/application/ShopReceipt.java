package com.asp.platform.shop.application;

import java.time.Instant;

/// The answer to a new order: its number, the status link's token (shown only this once), what the customer pays
/// at the counter and until when the order is kept.
public record ShopReceipt(int number, String statusToken, long totalCents, Instant holdUntil) {
}
