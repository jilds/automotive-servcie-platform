package com.asp.platform.config.web;

import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.asp.platform.identity.WorkshopDirectory;
import com.github.benmanes.caffeine.cache.Ticker;

import tools.jackson.databind.json.JsonMapper;

/// Wiring of the public workshop pages (ADR 0004). The filters run after Spring Security (order -100), whose public
/// chain lets every request through anonymously: first the per-address budget, then the slug lookup and tenant scope,
/// then the per-workshop rate limit.
@Configuration(proxyBeanMethods = false)
class PublicWebConfig implements WebMvcConfigurer {

    static final int LOOKUP_LIMIT_FILTER_ORDER = 5;
    static final int WORKSHOP_FILTER_ORDER = 10;
    static final int RATE_LIMIT_FILTER_ORDER = 20;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new PublicWorkshopArgumentResolver());
    }

    @Bean
    FilterRegistrationBean<PublicLookupLimiter> publicLookupLimiter(PublicRateLimitProperties properties,
            JsonMapper json) {
        var registration = new FilterRegistrationBean<>(
                new PublicLookupLimiter(properties, new ProblemResponses(json), Ticker.systemTicker()));
        registration.addUrlPatterns(PublicPaths.URL_PATTERN);
        registration.setOrder(LOOKUP_LIMIT_FILTER_ORDER);
        return registration;
    }

    /// `@Lazy`: the embedded server builds its filters before the rest of the context. Resolving the directory (and so
    /// JPA and the DataSource) on the first request keeps the database untouched at server start, also in tests whose
    /// datasource properties arrive later.
    @Bean
    FilterRegistrationBean<PublicWorkshopFilter> publicWorkshopFilter(@Lazy WorkshopDirectory directory,
            JsonMapper json) {
        var registration = new FilterRegistrationBean<>(new PublicWorkshopFilter(directory, new ProblemResponses(json)));
        registration.addUrlPatterns(PublicPaths.URL_PATTERN);
        registration.setOrder(WORKSHOP_FILTER_ORDER);
        return registration;
    }

    @Bean
    FilterRegistrationBean<PublicRateLimiter> publicRateLimiter(PublicRateLimitProperties properties,
            JsonMapper json) {
        var registration = new FilterRegistrationBean<>(
                new PublicRateLimiter(properties, new ProblemResponses(json), Ticker.systemTicker()));
        registration.addUrlPatterns(PublicPaths.URL_PATTERN);
        registration.setOrder(RATE_LIMIT_FILTER_ORDER);
        return registration;
    }
}
