/// Reports for the owner (plan 08): cost vs quote per delivered work order, mechanic productivity and the KPI screen,
/// with a CSV export of each. Read-only: plain SQL (`JdbcClient`) over the workshop's tables and the shared view
/// `work_order_line_amounts`, under row-level security. It depends on no other module (it reads tables, not
/// facades) and nothing depends on it.
@ApplicationModule(displayName = "Reporting", allowedDependencies = "shared")
@NullMarked
package com.asp.platform.reporting;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
