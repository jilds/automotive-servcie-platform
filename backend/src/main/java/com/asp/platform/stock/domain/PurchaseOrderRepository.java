package com.asp.platform.stock.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// Row-level security limits every query to the current workshop.
public interface PurchaseOrderRepository {

    PurchaseOrder save(PurchaseOrder order);

    Optional<PurchaseOrder> findById(UUID id);

    /// Newest first; null lists every status.
    List<PurchaseOrder> list(@Nullable PurchaseOrderStatus status);

    /// Numbers restart at 1 in every workshop.
    int nextNumber();
}
