package com.asp.platform.workshop.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// Row-level security limits every query to the current workshop.
public interface WorkOrderRepository {

    WorkOrder save(WorkOrder workOrder);

    Optional<WorkOrder> findById(UUID id);

    /// Numbers restart at 1 in every workshop.
    int nextNumber();

    List<WorkOrder> findByStatusIn(Collection<WorkOrderStatus> statuses);

    List<WorkOrder> findByMechanicAndStatusIn(UUID mechanicId, Collection<WorkOrderStatus> statuses);

    /// Newest first.
    List<WorkOrder> findByVehicle(UUID vehicleId);
}
