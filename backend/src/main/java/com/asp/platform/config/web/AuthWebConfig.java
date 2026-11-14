package com.asp.platform.config.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Ticker;

import tools.jackson.databind.json.JsonMapper;

/// Wiring of the sign-in rate limit ([AuthLimiter]). Runs after Spring Security (order -100), whose chain lets these
/// endpoints through anonymously, and before the public pages' filters.
@Configuration(proxyBeanMethods = false)
class AuthWebConfig {

    static final int AUTH_LIMIT_FILTER_ORDER = 3;

    @Bean
    FilterRegistrationBean<AuthLimiter> authLimiter(AuthRateLimitProperties properties, JsonMapper json) {
        var registration = new FilterRegistrationBean<>(
                new AuthLimiter(properties, new ProblemResponses(json), json, Ticker.systemTicker()));
        registration.addUrlPatterns("/api/v1/auth/*");
        registration.setOrder(AUTH_LIMIT_FILTER_ORDER);
        return registration;
    }
}
