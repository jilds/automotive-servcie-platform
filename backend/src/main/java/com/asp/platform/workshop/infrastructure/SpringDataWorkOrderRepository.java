package com.asp.platform.workshop.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.asp.platform.workshop.domain.WorkOrderStatus;

interface SpringDataWorkOrderRepository extends JpaRepository<WorkOrderEntity, UUID> {

    /// Row-level security scopes this to the current workshop. The unique (tenant_id, number) constraint
    /// catches the rare race of two simultaneous check-ins.
    @Query("select coalesce(max(w.number), 0) + 1 from WorkOrderEntity w")
    int nextNumber();

    @EntityGraph(attributePaths = "lines")
    List<WorkOrderEntity> findByStatusInOrderByStatusChangedAt(Collection<WorkOrderStatus> statuses);

    @EntityGraph(attributePaths = "lines")
    List<WorkOrderEntity> findByAssignedMechanicIdAndStatusInOrderByStatusChangedAt(UUID mechanicId,
            Collection<WorkOrderStatus> statuses);

    @EntityGraph(attributePaths = "lines")
    List<WorkOrderEntity> findByVehicleIdOrderByNumberDesc(UUID vehicleId);
}
