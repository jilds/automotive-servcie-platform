package com.asp.platform.workshop.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.asp.platform.shared.persistence.Upserts;
import com.asp.platform.workshop.domain.WorkOrder;
import com.asp.platform.workshop.domain.WorkOrderRepository;
import com.asp.platform.workshop.domain.WorkOrderStatus;

@Repository
class JpaWorkOrderRepository implements WorkOrderRepository {

    private final SpringDataWorkOrderRepository jpa;

    JpaWorkOrderRepository(SpringDataWorkOrderRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public WorkOrder save(WorkOrder order) {
        return Upserts.upsert(jpa, order.id(), order, WorkOrderEntity::new);
    }

    @Override
    public Optional<WorkOrder> findById(UUID id) {
        return jpa.findById(id).map(WorkOrderEntity::toDomain);
    }

    @Override
    public Optional<WorkOrder> lockById(UUID id) {
        return jpa.lockById(id).map(WorkOrderEntity::toDomain);
    }

    @Override
    public int nextNumber() {
        return jpa.nextNumber();
    }

    @Override
    public List<WorkOrder> findByStatusIn(Collection<WorkOrderStatus> statuses) {
        return jpa.findByStatusInOrderByStatusChangedAt(statuses).stream().map(WorkOrderEntity::toDomain).toList();
    }

    @Override
    public List<WorkOrder> findByMechanicAndStatusIn(UUID mechanicId, Collection<WorkOrderStatus> statuses) {
        return jpa.findByAssignedMechanicIdAndStatusInOrderByStatusChangedAt(mechanicId, statuses).stream()
                .map(WorkOrderEntity::toDomain).toList();
    }

    @Override
    public List<WorkOrder> findByVehicle(UUID vehicleId) {
        return jpa.findByVehicleIdOrderByNumberDesc(vehicleId).stream().map(WorkOrderEntity::toDomain).toList();
    }
}
