package com.asp.platform.billing.domain;

import com.asp.platform.shared.NotFoundException;

/// The work order is not in the workshop. 404 work-order-not-found, the code the workshop module uses.
public class BillingWorkOrderNotFoundException extends NotFoundException {

    public BillingWorkOrderNotFoundException() {
        super("work-order-not-found", "Work order not found");
    }
}
