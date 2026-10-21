/// Customers and their vehicles. Public API: [CustomerManagement] and [VehicleSummary].
/// Uses the identity module through [com.asp.platform.identity.WorkshopDirectory] (the workshop's country is
/// the default registration country of new vehicles).
@ApplicationModule(displayName = "Customers")
@NullMarked
package com.asp.platform.customer;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
