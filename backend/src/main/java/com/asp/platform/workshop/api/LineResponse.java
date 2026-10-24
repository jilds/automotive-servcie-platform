package com.asp.platform.workshop.api;

import java.math.BigDecimal;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.domain.LineType;
import com.asp.platform.workshop.domain.WorkOrderLine;

/// Price fields are null when the caller may not see prices (mechanics). `productId` is the stock product a part
/// line reserves, null for lines that do not come from stock. `serviceItemId` is the catalogue service a line was added
/// from, null for a line typed by hand.
record LineResponse(UUID id, int position, LineType type, String description, BigDecimal quantity,
        @Nullable Long unitPriceCents, @Nullable BigDecimal discountPercent, @Nullable BigDecimal vatRate,
        @Nullable Long netCents, @Nullable UUID productId, @Nullable UUID serviceItemId) {

    static LineResponse of(WorkOrderLine line, boolean showPrices) {
        if (!showPrices) {
            return new LineResponse(line.id(), line.position(), line.type(), line.description(), line.quantity(),
                    null, null, null, null, line.productId(), line.serviceItemId());
        }
        return new LineResponse(line.id(), line.position(), line.type(), line.description(), line.quantity(),
                line.unitPriceCents(), line.discountPercent(), line.vatRate(), line.netCents(), line.productId(),
                line.serviceItemId());
    }
}
