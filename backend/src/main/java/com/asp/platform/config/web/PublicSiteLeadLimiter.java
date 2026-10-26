package com.asp.platform.config.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

import com.github.benmanes.caffeine.cache.Ticker;

/// Limits the demo requests from the landing site: per client address (`app.public-site.leads`, 5 an hour by
/// default; IPv6 by its /64), then from all addresses together (`app.public-site.leads-total`, 50 an hour), so many
/// addresses cannot fill the table or flood the platform admins with mails. A request refused for its address does
/// not count towards the total. Over a limit: 429 `too-many-requests` with `Retry-After`, logged once per window.
/// Only `POST` counts, so a browser's CORS preflight does not use up the budget. In memory, per instance, like the
/// public page limits.
final class PublicSiteLeadLimiter extends OncePerRequestFilter {

    static final String PATH = "/api/v1/public-site/leads";
    private static final String ALL_ADDRESSES = "all";

    private final FixedWindowLimit leads;
    private final FixedWindowLimit leadsTotal;
    private final ProblemResponses problems;

    PublicSiteLeadLimiter(PublicSiteProperties properties, ProblemResponses problems, Ticker ticker) {
        this.leads = new FixedWindowLimit(properties.leads(), ticker);
        this.leadsTotal = new FixedWindowLimit(properties.leadsTotal(), ticker);
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Decoded and normalised like PublicPaths: servlet path plus path info.
        String path = request.getServletPath() + java.util.Objects.toString(request.getPathInfo(), "");
        return !"POST".equals(request.getMethod()) || !PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        FixedWindowLimit.Outcome outcome = leads.acquire(ClientAddresses.key(request.getRemoteAddr()));
        if (!outcome.refused()) {
            outcome = leadsTotal.acquire(ALL_ADDRESSES);
        }
        if (outcome.refused()) {
            problems.tooManyRequests(request, response, "public-site", outcome);
            return;
        }
        chain.doFilter(request, response);
    }
}
