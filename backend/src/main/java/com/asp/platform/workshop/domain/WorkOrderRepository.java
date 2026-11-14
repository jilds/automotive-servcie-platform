package com.asp.platform.workshop.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// Row-level security limits every query to the current workshop.
public interface WorkOrderRepository {

    WorkOrder save(WorkOrder workOrder);

    Optional<WorkOrder> findById(UUID id);

    /// Reads the order and locks its row until the transaction ends (`PESSIMISTIC_WRITE`), so sending a quote, a line
    /// change, a decision, the expiry and a status move of one order run one after the other. Waits 3 s at most,
    /// then fails with a pessimistic locking error (409 `try-again`). Call it before reading the order or its quotes
    /// in the transaction, so they are read after the lock.
    Optional<WorkOrder> lockById(UUID id);

    /// Numbers restart at 1 in every workshop.
    int nextNumber();

    List<WorkOrder> findByStatusIn(Collection<WorkOrderStatus> statuses);

    List<WorkOrder> findByMechanicAndStatusIn(UUID mechanicId, Collection<WorkOrderStatus> statuses);

    /// Orders in the open statuses, plus delivered ones changed since `deliveredSince` that were not hidden.
    List<WorkOrder> findBoard(Collection<WorkOrderStatus> open, java.time.Instant deliveredSince);

    List<WorkOrder> findBoardOf(UUID mechanicId, Collection<WorkOrderStatus> open, java.time.Instant deliveredSince);

    /// Hides a DELIVERED order from the board without changing its status; false when it is not delivered.
    boolean hideFromBoard(UUID id, UUID userId, java.time.Instant now);

    /// Hides every delivered order still on the board; returns how many.
    int hideAllDeliveredFromBoard(UUID userId, java.time.Instant now);

    /// Newest first.
    List<WorkOrder> findByVehicle(UUID vehicleId);
}
