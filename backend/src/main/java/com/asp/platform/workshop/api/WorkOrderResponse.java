package com.asp.platform.workshop.api;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shared.CurrentUser;
import com.asp.platform.workshop.application.VehiclePlate;
import com.asp.platform.workshop.application.WorkOrderView;
import com.asp.platform.workshop.domain.CheckInDetails;
import com.asp.platform.workshop.domain.WorkOrder;
import com.asp.platform.workshop.domain.WorkOrderStatus;

/// Totals are null when the caller may not see prices (mechanics). The plate fields are null when the
/// vehicle is not found.
record WorkOrderResponse(UUID id, int number, UUID vehicleId, @Nullable String plate, @Nullable String plateDisplay,
        @Nullable String plateEra, @Nullable String registrationCountry, @Nullable YearMonth registrationDate,
        WorkOrderStatus status, List<WorkOrderStatus> allowedNext, @Nullable Integer mileageIn,
        @Nullable Integer fuelLevel, @Nullable String complaint, @Nullable String damageNotes,
        @Nullable UUID assignedMechanicId, Instant statusChangedAt, List<LineResponse> lines,
        @Nullable Long netCents, @Nullable Long vatCents, @Nullable Long totalCents) {

    static WorkOrderResponse of(WorkOrderView view, CurrentUser user) {
        WorkOrder order = view.order();
        CheckInDetails checkIn = order.checkIn();
        VehiclePlate plate = view.plate();
        boolean prices = user.canSeePrices();
        return new WorkOrderResponse(order.id(), order.number(), order.vehicleId(),
                plate == null ? null : plate.plate(),
                plate == null ? null : plate.display(),
                plate == null ? null : plate.era(),
                plate == null ? null : plate.registrationCountry(),
                plate == null ? null : plate.registrationDate(),
                order.status(), List.copyOf(order.status().next()), checkIn.mileageIn(), checkIn.fuelLevel(),
                checkIn.complaint(), checkIn.damageNotes(), order.assignedMechanicId(), order.statusChangedAt(),
                order.lines().stream().map(line -> LineResponse.of(line, prices)).toList(),
                prices ? order.netCents() : null,
                prices ? order.vatCents() : null,
                prices ? order.totalCents() : null);
    }
}
