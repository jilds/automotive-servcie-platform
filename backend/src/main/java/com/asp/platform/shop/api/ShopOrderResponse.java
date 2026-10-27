package com.asp.platform.shop.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shop.application.ShopOrderView;
import com.asp.platform.shop.domain.CancelledBy;
import com.asp.platform.shop.domain.PaymentMethod;
import com.asp.platform.shop.domain.ShopContact;
import com.asp.platform.shop.domain.ShopOrder;
import com.asp.platform.shop.domain.ShopOrderLine;
import com.asp.platform.shop.domain.ShopOrderStatus;

/// An order for the front desk, with the contact and the product snapshot of each line.
///
/// @param cancelledBy CUSTOMER, STAFF or EXPIRY; null unless CANCELLED or EXPIRED
record ShopOrderResponse(UUID id, int number, ShopOrderStatus status, Instant placedAt, @Nullable Instant readyAt,
        Instant holdUntil, String name, @Nullable String phone, @Nullable String email, @Nullable String nif,
        boolean marketingConsent, List<Line> lines, long totalCents, @Nullable PaymentMethod paymentMethod,
        @Nullable String collectedByName, @Nullable Instant collectedAt, @Nullable String cancelReason,
        @Nullable CancelledBy cancelledBy, long version) {

    record Line(UUID productId, String sku, String name, String unit, int quantity, long unitPriceCents,
            BigDecimal vatRate, long unitPriceWithVatCents, long lineTotalCents) {

        static Line of(ShopOrderLine l) {
            return new Line(l.productId(), l.sku(), l.name(), l.unit(), l.quantity(), l.unitPriceCents(),
                    l.vatRate(), l.unitPriceWithVatCents(), l.lineTotalCents());
        }
    }

    static ShopOrderResponse of(ShopOrderView view) {
        ShopOrder o = view.order();
        ShopContact c = o.contact();
        return new ShopOrderResponse(o.id(), o.number(), o.status(), o.placedAt(), o.readyAt(), o.holdUntil(),
                c.name(), c.phone(), c.email(), c.nif(), o.marketingConsent(),
                o.lines().stream().map(Line::of).toList(), o.totalCents(), o.paymentMethod(), view.collectedByName(),
                o.collectedAt(), o.cancelReason(), o.cancelledBy(), o.version());
    }
}
