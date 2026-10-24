package com.asp.platform.stock.domain;

import java.util.List;
import java.util.UUID;

/// The stock ledger: rows are added, never changed or removed.
public interface StockMovementRepository {

    void add(StockMovement movement);

    /// Newest first, at most `limit` rows.
    List<StockMovement> findByProduct(UUID productId, int limit);
}
