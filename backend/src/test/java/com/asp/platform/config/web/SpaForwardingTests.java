package com.asp.platform.config.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.asp.platform.ApiTestSupport;

/// A reload or a deep link on an Angular route must get the app, not a 404 from the server. Keep the list in sync
/// with `frontend/src/app/app.routes.ts` and the feature route files.
class SpaForwardingTests extends ApiTestSupport {

    @ParameterizedTest
    @ValueSource(strings = {"/login", "/activate", "/w/oficina-azul", "/admin", "/admin/workshops/1", "/board",
            "/check-in", "/customers", "/customers/1", "/vehicles/1", "/work-orders/1", "/backoffice",
            "/backoffice/team", "/stock", "/stock/purchase-orders", "/backoffice/services", "/backoffice/settings",
            "/w/oficina-azul/book", "/w/oficina-azul/booking/abc_DEF-123", "/bookings", "/backoffice/booking"})
    void should_serve_the_app_on_its_routes(String path) throws Exception {
        mvc.perform(get(path)).andExpect(forwardedUrl("/index.html"));
    }
}
