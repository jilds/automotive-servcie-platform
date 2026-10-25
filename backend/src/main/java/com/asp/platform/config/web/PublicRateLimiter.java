package com.asp.platform.config.web;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

import com.github.benmanes.caffeine.cache.Ticker;

/// Limits what one client address may send to one workshop's public pages (ADR 0004): reads and writes each in a
/// fixed window (`app.public.rate-limit.reads` / `writes`). Over the limit: 429 `too-many-requests` with
/// `Retry-After` in seconds. Runs after [PublicWorkshopFilter], so only open workshops count here; [PublicLookupLimiter]
/// has already counted the request against the address's budget across all slugs.
///
/// The counters live in this instance's memory: with several instances each allows the limit, and a restart clears
/// them. Enough against form spam on one instance; a shared store is an improvement for later.
final class PublicRateLimiter extends OncePerRequestFilter {

    private static final Set<String> READS = Set.of("GET", "HEAD", "OPTIONS");

    private final FixedWindowLimit reads;
    private final FixedWindowLimit writes;
    private final ProblemResponses problems;

    PublicRateLimiter(PublicRateLimitProperties properties, ProblemResponses problems, Ticker ticker) {
        this.reads = new FixedWindowLimit(properties.reads(), ticker);
        this.writes = new FixedWindowLimit(properties.writes(), ticker);
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PublicPaths.isPublic(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String slug = PublicPaths.slug(request).orElse("");
        String key = ClientAddresses.key(request.getRemoteAddr()) + '|' + slug;
        FixedWindowLimit limit = READS.contains(request.getMethod()) ? reads : writes;
        FixedWindowLimit.Outcome outcome = limit.acquire(key);
        if (outcome.refused()) {
            problems.tooManyRequests(request, response, slug, outcome);
            return;
        }
        chain.doFilter(request, response);
    }
}
