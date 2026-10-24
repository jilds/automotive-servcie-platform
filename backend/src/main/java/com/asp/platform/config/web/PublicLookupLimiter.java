package com.asp.platform.config.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

import com.github.benmanes.caffeine.cache.Ticker;

/// The first public filter (ADR 0004): every request of one client address to `/api/v1/public/**`, across all
/// slugs, counts against `app.public.rate-limit.lookups` before [PublicWorkshopFilter] looks the slug up. So a flood
/// of unknown or closed slugs, which [PublicRateLimiter] never sees, gets 429 `too-many-requests` as well.
/// Same in-memory, per-instance counters as [PublicRateLimiter].
final class PublicLookupLimiter extends OncePerRequestFilter {

    private final FixedWindowLimit lookups;
    private final ProblemResponses problems;

    PublicLookupLimiter(PublicRateLimitProperties properties, ProblemResponses problems, Ticker ticker) {
        this.lookups = new FixedWindowLimit(properties.lookups(), ticker);
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PublicPaths.isPublic(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long retryAfterSeconds = lookups.acquire(ClientAddresses.key(request.getRemoteAddr()));
        if (retryAfterSeconds > 0) {
            problems.tooManyRequests(request, response, PublicPaths.slug(request).orElse(""), retryAfterSeconds);
            return;
        }
        chain.doFilter(request, response);
    }
}
