package com.asp.platform.workshop.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.workshop.BillableWorkOrder;
import com.asp.platform.workshop.WorkOrderBilling;
import com.asp.platform.workshop.domain.WorkOrder;
import com.asp.platform.workshop.domain.WorkOrderRepository;

/// Gives the billing module a read-only copy of a work order (row-level security limits it to the workshop).
@Service
class WorkOrderBillingService implements WorkOrderBilling {

    private final WorkOrderRepository orders;

    WorkOrderBillingService(WorkOrderRepository orders) {
        this.orders = orders;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BillableWorkOrder> billable(UUID workOrderId) {
        return orders.findById(workOrderId).map(WorkOrderBillingService::of);
    }

    private static BillableWorkOrder of(WorkOrder order) {
        return new BillableWorkOrder(order.id(), order.number(), order.status().name(), order.vehicleId(),
                order.lines().stream()
                        .map(l -> new BillableWorkOrder.Line(l.id(), l.position(), l.type().name(), l.description(),
                                l.quantity(), l.unitPriceCents(), l.discountPercent(), l.vatRate(), l.netCents(),
                                l.vatCents(), l.serviceItemId()))
                        .toList());
    }
}
