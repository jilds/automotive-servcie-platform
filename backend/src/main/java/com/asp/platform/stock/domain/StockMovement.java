package com.asp.platform.stock.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// One row of the stock ledger. Rows are only ever added: the quantity on hand of a product is the sum of its
/// movements.
///
/// @param quantity  positive for what came in, negative for what went out
/// @param reference the document behind the movement; null for an adjustment
/// @param reason    why stock was adjusted; required for an adjustment
/// @param changedBy the user who made the change
public record StockMovement(UUID id, UUID productId, MovementType type, BigDecimal quantity,
        @Nullable StockReference reference, @Nullable String reason, UUID changedBy, Instant changedAt) {

    public static final int MAX_REASON = 500;

    public StockMovement {
        Quantities.fits(quantity, "Quantity");
        reason = Text.optional(reason, "Reason", MAX_REASON);
        switch (type) {
            case RECEIPT -> {
                if (quantity.signum() <= 0) {
                    throw new IllegalArgumentException("A receipt adds stock");
                }
            }
            case ISSUE -> {
                if (quantity.signum() >= 0) {
                    throw new IllegalArgumentException("An issue takes stock away");
                }
            }
            case ADJUSTMENT -> {
                if (quantity.signum() == 0) {
                    throw new IllegalArgumentException("An adjustment changes the quantity");
                }
                if (reason == null) {
                    throw new AdjustmentReasonRequiredException();
                }
            }
        }
    }
}
