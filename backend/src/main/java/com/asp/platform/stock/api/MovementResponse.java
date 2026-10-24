package com.asp.platform.stock.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.stock.application.MovementView;
import com.asp.platform.stock.domain.MovementType;
import com.asp.platform.stock.domain.StockMovement;
import com.asp.platform.stock.domain.StockReference;

/// A ledger row. `quantity` is positive for what came in and negative for what went out. `referenceType` is
/// `WORK_ORDER` or `PURCHASE_ORDER` (null for an adjustment).
record MovementResponse(UUID id, MovementType type, BigDecimal quantity, @Nullable String referenceType,
        @Nullable UUID referenceId, @Nullable String reason, UUID changedById, @Nullable String changedByName,
        Instant changedAt) {

    static MovementResponse of(MovementView view) {
        StockMovement m = view.movement();
        StockReference reference = m.reference();
        return new MovementResponse(m.id(), m.type(), m.quantity(), reference == null ? null : reference.type(),
                reference == null ? null : reference.id(), m.reason(), m.changedBy(), view.changedByName(),
                m.changedAt());
    }
}
