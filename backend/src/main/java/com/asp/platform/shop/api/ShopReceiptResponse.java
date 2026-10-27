package com.asp.platform.shop.api;

import java.time.Instant;
import java.time.ZoneId;

import com.asp.platform.shop.application.ShopReceipt;

/// The answer to a new order: its number and the status link's token, shown only this once.
///
/// @param timeZone the workshop's IANA zone, in which the page shows `holdUntil`
record ShopReceiptResponse(int number, String statusToken, long totalCents, Instant holdUntil, String timeZone) {

    static ShopReceiptResponse of(ShopReceipt receipt, ZoneId zone) {
        return new ShopReceiptResponse(receipt.number(), receipt.statusToken(), receipt.totalCents(),
                receipt.holdUntil(), zone.getId());
    }
}
