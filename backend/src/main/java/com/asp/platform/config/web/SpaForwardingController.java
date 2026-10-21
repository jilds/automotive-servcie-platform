package com.asp.platform.config.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/// Serves the Angular app for its client-side routes, so a reload or deep link opens the right page.
/// Keep in sync with `frontend/src/app/app.routes.ts`.
@Controller
class SpaForwardingController {

    @GetMapping({
            "/login",
            "/signup",
            "/board",
            "/check-in",
            "/customers",
            "/customers/{id}",
            "/vehicles/{id}",
            "/work-orders/{id}",
            "/backoffice",
            "/backoffice/team",
            "/backoffice/settings"
    })
    String forward() {
        return "forward:/index.html";
    }
}
