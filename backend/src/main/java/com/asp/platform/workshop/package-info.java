/// Workshop floor: check-in, work orders with labor and parts, the Kanban board and service history.
/// Exposes no public API yet; it uses the customer module through [com.asp.platform.customer.CustomerManagement].
@ApplicationModule(displayName = "Workshop")
@NullMarked
package com.asp.platform.workshop;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
