package com.asp.platform.config.prod;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

/// What the `prod` profile changes: no API docs, actuator on the management port only (with Prometheus), the
/// security headers, and the client address taken from `X-Forwarded-For` when the peer is a trusted proxy.
class ProdProfileServerTests extends ProdProfileServerTestSupport {

    @Test
    void should_not_serve_the_api_docs_or_swagger_ui() throws Exception {
        assertThat(get("/v3/api-docs").statusCode()).isEqualTo(404);
        assertThat(get("/swagger-ui.html").statusCode()).isEqualTo(404);
        assertThat(get("/swagger-ui/index.html").statusCode()).isEqualTo(404);
    }

    @Test
    void should_serve_health_probes_and_prometheus_on_the_management_port_only() throws Exception {
        assertThat(get(managementPort, "/actuator/health/liveness", Map.of()).statusCode()).isEqualTo(200);
        assertThat(get(managementPort, "/actuator/health/readiness", Map.of()).statusCode()).isEqualTo(200);
        HttpResponse<String> metrics = get(managementPort, "/actuator/prometheus", Map.of());
        assertThat(metrics.statusCode()).isEqualTo(200);
        assertThat(metrics.body()).contains("jvm_memory_used_bytes");

        assertThat(get("/actuator/health").statusCode()).isEqualTo(404);
        assertThat(get("/actuator/prometheus").statusCode()).isEqualTo(404);
    }

    @Test
    void should_send_the_security_headers_and_hsts_on_https_through_the_proxy() throws Exception {
        HttpResponse<String> plain = get("/api/v1/platform/contact");
        assertThat(plain.headers().firstValue("Content-Security-Policy")).hasValueSatisfying(csp -> assertThat(csp)
                .contains("script-src 'self'", "frame-ancestors 'none'", "object-src 'none'"));
        assertThat(plain.headers().firstValue("Referrer-Policy")).hasValue("same-origin");
        assertThat(plain.headers().firstValue("Permissions-Policy")).hasValueSatisfying(p -> assertThat(p)
                .contains("camera=()"));
        assertThat(plain.headers().firstValue("X-Frame-Options")).hasValue("DENY");
        assertThat(plain.headers().firstValue("Strict-Transport-Security")).as("plain HTTP").isEmpty();

        HttpResponse<String> https = get("/api/v1/platform/contact", Map.of("X-Forwarded-Proto", "https"));
        assertThat(https.headers().firstValue("Strict-Transport-Security"))
                .hasValueSatisfying(h -> assertThat(h).contains("max-age=31536000", "includeSubDomains"));
    }

    @Test
    void should_take_the_client_address_from_a_trusted_proxy() throws Exception {
        // The test client connects from 127.0.0.1, a trusted proxy by default.
        HttpResponse<String> probe = get("/test-probe/remote-address",
                Map.of("X-Forwarded-For", "203.0.113.7", "X-Forwarded-Proto", "https"));

        assertThat((String) JsonPath.read(probe.body(), "$.remoteAddr")).isEqualTo("203.0.113.7");
        assertThat((Boolean) JsonPath.read(probe.body(), "$.secure")).isTrue();
    }
}
