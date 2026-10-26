package com.asp.platform.config.web;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.github.benmanes.caffeine.cache.Ticker;

import tools.jackson.databind.json.JsonMapper;

/// Wiring of `/api/v1/public-site/**`, the landing site's API (step 2c): the per-address limit on demo requests and
/// the CORS rule that lets only `app.public-site.origin` post from the browser and read `Retry-After`.
@Configuration(proxyBeanMethods = false)
public class PublicSiteWebConfig {

    @Bean
    FilterRegistrationBean<PublicSiteLeadLimiter> publicSiteLeadLimiter(PublicSiteProperties properties,
            JsonMapper json) {
        var registration = new FilterRegistrationBean<>(
                new PublicSiteLeadLimiter(properties, new ProblemResponses(json), Ticker.systemTicker()));
        registration.addUrlPatterns(PublicSiteLeadLimiter.PATH);
        registration.setOrder(PublicWebConfig.LOOKUP_LIMIT_FILTER_ORDER);
        return registration;
    }

    /// Used by the public-site security chain. No origin set: no CORS headers, so only same-origin pages can post.
    public static CorsConfigurationSource corsFor(PublicSiteProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        String origin = properties.originOrNull();
        if (origin != null) {
            CorsConfiguration cors = new CorsConfiguration();
            cors.setAllowedOrigins(List.of(origin));
            cors.setAllowedMethods(List.of("POST"));
            cors.setAllowedHeaders(List.of("Content-Type"));
            // The site reads Retry-After on a 429 to say when to try again.
            cors.setExposedHeaders(List.of(HttpHeaders.RETRY_AFTER));
            cors.setAllowCredentials(false);
            cors.setMaxAge(Duration.ofHours(1));
            source.registerCorsConfiguration("/api/v1/public-site/**", cors);
        }
        return source;
    }
}
