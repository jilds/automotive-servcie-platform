package com.asp.platform.workshop.api;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shared.CurrentUser;
import com.asp.platform.workshop.application.QuoteSummary;
import com.asp.platform.workshop.application.VehiclePlate;
import com.asp.platform.workshop.application.WorkOrderView;
import com.asp.platform.workshop.domain.CheckInDetails;
import com.asp.platform.workshop.domain.WorkOrder;
import com.asp.platform.workshop.domain.WorkOrderStatus;

/// Totals are null when the caller may not see prices (mechanics). The plate fields are null when the
/// vehicle is not found. `readOnly` is true for a mechanic looking at a job that is not theirs (opened from the
/// vehicle history): no status moves are offered (`allowedNext` is empty) and none is accepted. `quote` is the latest
/// quote without amounts (`status` EXPIRED for a SENT quote past `validUntil`), also for mechanics; null when none.
record WorkOrderResponse(UUID id, int number, UUID vehicleId, @Nullable String plate, @Nullable String plateDisplay,
        @Nullable String plateEra, @Nullable String registrationCountry, @Nullable YearMonth registrationDate,
        WorkOrderStatus status, List<WorkOrderStatus> allowedNext, @Nullable Integer mileageIn,
        @Nullable Integer fuelLevel, @Nullable String complaint, @Nullable String damageNotes,
        @Nullable UUID assignedMechanicId, Instant statusChangedAt, List<LineResponse> lines,
        @Nullable Long netCents, @Nullable Long vatCents, @Nullable Long totalCents, boolean readOnly,
        @Nullable QuoteSummary quote) {

    static WorkOrderResponse of(WorkOrderView view, CurrentUser user) {
        WorkOrder order = view.order();
        CheckInDetails checkIn = order.checkIn();
        VehiclePlate plate = view.plate();
        boolean prices = user.canSeePrices();
        boolean readOnly = user.isMechanic() && !order.isAssignedTo(user.userId());
        return new WorkOrderResponse(order.id(), order.number(), order.vehicleId(),
                plate == null ? null : plate.plate(),
                plate == null ? null : plate.display(),
                plate == null ? null : plate.era(),
                plate == null ? null : plate.registrationCountry(),
                plate == null ? null : plate.registrationDate(),
                order.status(), readOnly ? List.of() : List.copyOf(order.status().next()), checkIn.mileageIn(),
                checkIn.fuelLevel(),
                checkIn.complaint(), checkIn.damageNotes(), order.assignedMechanicId(), order.statusChangedAt(),
                order.lines().stream().map(line -> LineResponse.of(line, prices)).toList(),
                prices ? order.netCents() : null,
                prices ? order.vatCents() : null,
                prices ? order.totalCents() : null,
                readOnly,
                view.quote());
    }
}
