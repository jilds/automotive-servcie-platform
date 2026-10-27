package com.asp.platform.shop.api;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shop.application.PublicOrderView;
import com.asp.platform.shop.domain.ShopOrder;
import com.asp.platform.shop.domain.ShopOrderLine;
import com.asp.platform.shop.domain.ShopOrderStatus;

/// An order as its status link shows it: no contact data, no internal ids (the token is the only key).
/// `timeZone` is the workshop's IANA zone id, to show the times in local time.
record PublicShopOrderResponse(int number, ShopOrderStatus status, Instant placedAt, Instant holdUntil,
        String timeZone, String workshopName, @Nullable String pickupNote, List<Line> lines, long totalCents,
        boolean canCancel) {

    record Line(String name, String unit, int quantity, long unitPriceWithVatCents, long lineTotalCents) {

        static Line of(ShopOrderLine l) {
            return new Line(l.name(), l.unit(), l.quantity(), l.unitPriceWithVatCents(), l.lineTotalCents());
        }
    }

    static PublicShopOrderResponse of(PublicOrderView view) {
        ShopOrder o = view.order();
        return new PublicShopOrderResponse(o.number(), o.status(), o.placedAt(), o.holdUntil(),
                view.timeZone().getId(), view.workshopName(), view.pickupNote(),
                o.lines().stream().map(Line::of).toList(), o.totalCents(), o.canCancel());
    }
}
