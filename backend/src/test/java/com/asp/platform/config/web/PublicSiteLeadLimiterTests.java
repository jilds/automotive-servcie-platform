package com.asp.platform.config.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.github.benmanes.caffeine.cache.Ticker;

import tools.jackson.databind.json.JsonMapper;

/// The landing site's demo request limits (step 2c), on a fake clock: per address, then all addresses together.
class PublicSiteLeadLimiterTests {

    private final AtomicLong nanos = new AtomicLong(1_000_000_000L);
    private final Ticker ticker = nanos::get;
    private final PublicSiteLeadLimiter limiter = new PublicSiteLeadLimiter(
            properties("https://asp.example", 2, 3), new ProblemResponses(JsonMapper.builder().build()), ticker);

    private static PublicSiteProperties properties(String origin, int perAddress, int total) {
        return new PublicSiteProperties(origin, new PublicRateLimitProperties.Rule(perAddress, Duration.ofHours(1)),
                new PublicRateLimitProperties.Rule(total, Duration.ofHours(1)));
    }

    @Test
    void should_cap_all_addresses_together_after_each_address_s_own_limit() throws Exception {
        assertThat(post("10.0.0.1")).isEqualTo(201);
        assertThat(post("10.0.0.2")).isEqualTo(201);
        assertThat(post("10.0.0.3")).isEqualTo(201);

        // A fourth address, within its own limit, meets the total.
        MockHttpServletResponse refused = send("10.0.0.4");
        assertThat(refused.getStatus()).isEqualTo(429);
        assertThat(refused.getHeader("Retry-After")).isEqualTo("3600");

        nanos.addAndGet(Duration.ofHours(1).plusSeconds(1).toNanos());
        assertThat(post("10.0.0.4")).isEqualTo(201);
    }

    @Test
    void should_not_count_a_request_refused_for_its_address_towards_the_total() throws Exception {
        assertThat(post("10.0.0.1")).isEqualTo(201);
        assertThat(post("10.0.0.1")).isEqualTo(201);
        assertThat(post("10.0.0.1")).isEqualTo(429);
        assertThat(post("10.0.0.1")).isEqualTo(429);

        // Two of the total's three places were used, so one is left.
        assertThat(post("10.0.0.2")).isEqualTo(201);
        assertThat(post("10.0.0.3")).isEqualTo(429);
    }

    @Test
    void should_leave_preflights_and_other_paths_alone() {
        MockHttpServletRequest preflight = new MockHttpServletRequest("OPTIONS", PublicSiteLeadLimiter.PATH);
        preflight.setServletPath(PublicSiteLeadLimiter.PATH);
        MockHttpServletRequest other = new MockHttpServletRequest("POST", "/api/v1/public-site/other");
        other.setServletPath("/api/v1/public-site/other");

        assertThat(limiter.shouldNotFilter(preflight)).isTrue();
        assertThat(limiter.shouldNotFilter(other)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "https://asp.example", "http://localhost:4200", " https://asp.example "})
    void should_accept_an_empty_origin_or_scheme_host_and_port(String origin) {
        assertThat(violations(origin)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "https://*.asp.example", "https://asp.example/", "https://asp.example/form",
            "asp.example", "ftp://asp.example"})
    void should_refuse_a_wildcard_a_path_or_another_scheme(String origin) {
        assertThat(violations(origin)).extracting(v -> v.getPropertyPath().toString()).containsExactly("origin");
    }

    private static Set<ConstraintViolation<PublicSiteProperties>> violations(String origin) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            return validator.validate(properties(origin, 5, 50));
        }
    }

    private int post(String address) throws Exception {
        return send(address).getStatus();
    }

    private MockHttpServletResponse send(String address) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", PublicSiteLeadLimiter.PATH);
        request.setServletPath(PublicSiteLeadLimiter.PATH);
        request.setRemoteAddr(address);
        MockHttpServletResponse response = new MockHttpServletResponse();
        limiter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                ((MockHttpServletResponse) res).setStatus(201);
            }
        });
        return response;
    }
}
