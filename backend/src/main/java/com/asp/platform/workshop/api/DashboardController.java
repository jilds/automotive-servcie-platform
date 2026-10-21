package com.asp.platform.workshop.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.shared.CurrentUser;
import com.asp.platform.workshop.application.DashboardService;

/// Revenue and team workload are for the owner only.
@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("hasRole('OWNER')")
class DashboardController {

    private final DashboardService dashboards;

    DashboardController(DashboardService dashboards) {
        this.dashboards = dashboards;
    }

    @GetMapping
    DashboardResponse dashboard(CurrentUser user) {
        return DashboardResponse.of(dashboards.dashboard(user.tenantId()));
    }
}
