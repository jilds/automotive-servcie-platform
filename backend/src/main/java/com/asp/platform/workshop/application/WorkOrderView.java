package com.asp.platform.workshop.application;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.domain.WorkOrder;

/// A work order with the vehicle plate it is shown with; the plate is null when the vehicle is not found. `quote` is
/// the order's latest quote, or null when none was sent.
public record WorkOrderView(WorkOrder order, @Nullable VehiclePlate plate, @Nullable QuoteSummary quote) {
}
