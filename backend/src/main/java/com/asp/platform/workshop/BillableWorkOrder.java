package com.asp.platform.workshop;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// A work order as an invoice copies it. The amounts are the work order's own, rounded line by line half up.
///
/// @param status    the work order status by name (DIAGNOSTICS ... DELIVERED, CANCELLED)
/// @param vehicleId the vehicle, whose owner the invoice is addressed to unless the request says otherwise
public record BillableWorkOrder(UUID id, int number, String status, UUID vehicleId, List<Line> lines) {

    /// Only a delivered work order is final: its lines are locked and its stock consumed.
    public boolean delivered() {
        return "DELIVERED".equals(status);
    }

    /// @param type          "LABOR" or "PART"
    /// @param sourceLineId  the id of the work order line
    public record Line(UUID sourceLineId, int position, String type, String description, BigDecimal quantity,
            long unitPriceCents, BigDecimal discountPercent, BigDecimal vatRate, long netCents, long vatCents,
            @Nullable UUID serviceItemId) {
    }
}
