package com.asp.platform.config.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/// Serves the Angular app for its client-side routes, so a reload or deep link opens the right page.
/// Keep in sync with `frontend/src/app/app.routes.ts`.
@Controller
class SpaForwardingController {

    @GetMapping({
            "/login",
            "/activate",
            "/w/{slug}",
            "/w/{slug}/book",
            "/w/{slug}/booking/{token}",
            "/admin",
            "/admin/workshops/{id}",
            "/board",
            "/check-in",
            "/customers",
            "/customers/{id}",
            "/vehicles/{id}",
            "/work-orders/{id}",
            "/stock",
            "/stock/purchase-orders",
            "/backoffice",
            "/backoffice/team",
            "/backoffice/services",
            "/backoffice/settings",
            "/backoffice/booking",
            "/bookings"
    })
    String forward() {
        return "forward:/index.html";
    }
}
