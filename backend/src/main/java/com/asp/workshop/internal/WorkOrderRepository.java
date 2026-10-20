package com.asp.workshop.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.asp.workshop.WorkOrderStatus;

interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {

    /** Row-level security scopes this to the current workshop, so numbers restart at 1 per workshop. */
    @Query("select coalesce(max(w.number), 0) + 1 from WorkOrder w")
    int nextNumber();

    List<WorkOrder> findByStatusInOrderByStatusChangedAt(Collection<WorkOrderStatus> statuses);

    List<WorkOrder> findByAssignedMechanicIdAndStatusInOrderByStatusChangedAt(UUID mechanicId,
            Collection<WorkOrderStatus> statuses);

    List<WorkOrder> findByVehicleIdOrderByNumberDesc(UUID vehicleId);
}
