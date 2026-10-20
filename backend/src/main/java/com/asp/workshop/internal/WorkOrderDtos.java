package com.asp.workshop.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import com.asp.workshop.WorkOrderStatus;

final class WorkOrderDtos {

    private WorkOrderDtos() {
    }

    record CheckInRequest(@NotNull UUID vehicleId, @PositiveOrZero Integer mileageIn,
            @Min(0) @Max(8) Integer fuelLevel, String complaint, String damageNotes, UUID assignedMechanicId) {
    }

    record LineRequest(@NotNull LineType type, @NotBlank String description, @NotNull @Positive BigDecimal quantity,
            @NotNull @PositiveOrZero Long unitPriceCents, @DecimalMin("0") @DecimalMax("100") BigDecimal discountPercent,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal vatRate) {
    }

    record StatusRequest(@NotNull WorkOrderStatus status) {
    }

    record AssignRequest(UUID mechanicId) {
    }

    /** Price fields are null when the caller is a mechanic. */
    record LineResponse(UUID id, int position, LineType type, String description, BigDecimal quantity,
            Long unitPriceCents, BigDecimal discountPercent, BigDecimal vatRate, Long netCents) {
    }

    record WorkOrderResponse(UUID id, int number, UUID vehicleId, String plate, WorkOrderStatus status,
            List<WorkOrderStatus> allowedNext, Integer mileageIn, Integer fuelLevel, String complaint,
            String damageNotes, UUID assignedMechanicId, Instant statusChangedAt, List<LineResponse> lines,
            Long netCents, Long vatCents, Long totalCents) {
    }

    record BoardCard(UUID id, int number, String plate, String vehicle, UUID assignedMechanicId,
            Instant statusChangedAt) {
    }

    record BoardResponse(Map<WorkOrderStatus, List<BoardCard>> columns) {
    }
}
