package com.asp.platform.config.web;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/// Test-only: the client address and scheme the application sees, after Tomcat applied (or ignored) the
/// `X-Forwarded-*` headers. The public rate limits key on the same `getRemoteAddr()`.
@RestController
class RemoteAddressProbeController {

    @GetMapping("/test-probe/remote-address")
    Map<String, Object> remoteAddress(HttpServletRequest request) {
        return Map.of("remoteAddr", request.getRemoteAddr(), "secure", request.isSecure());
    }
}
