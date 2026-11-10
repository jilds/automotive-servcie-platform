package com.asp.platform.workshop;

import java.util.Optional;
import java.util.UUID;

/// Public API of the workshop module for the billing module: a work order as the invoice copies it. Read only; the
/// workshop knows nothing about invoices (plan 07).
public interface WorkOrderBilling {

    /// The work order with its current lines and amounts; empty when it is not in the workshop.
    Optional<BillableWorkOrder> billable(UUID workOrderId);
}
