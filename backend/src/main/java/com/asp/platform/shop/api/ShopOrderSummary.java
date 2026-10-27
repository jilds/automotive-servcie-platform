package com.asp.platform.shop.api;

import java.time.Instant;
import java.util.UUID;

import com.asp.platform.shop.application.ShopOrderView;
import com.asp.platform.shop.domain.ShopOrder;
import com.asp.platform.shop.domain.ShopOrderStatus;

/// One row of the front desk's order list.
record ShopOrderSummary(UUID id, int number, ShopOrderStatus status, Instant placedAt, Instant holdUntil, String name,
        int lineCount, long totalCents, long version) {

    static ShopOrderSummary of(ShopOrderView view) {
        ShopOrder o = view.order();
        return new ShopOrderSummary(o.id(), o.number(), o.status(), o.placedAt(), o.holdUntil(), o.contact().name(),
                o.lines().size(), o.totalCents(), o.version());
    }
}
