package com.asp.platform.config.web;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

import com.asp.platform.identity.PublicWorkshop;
import com.asp.platform.identity.WorkshopDirectory;
import com.asp.platform.shared.PublicWorkshopRef;
import com.asp.platform.shared.TenantScope;

/// Serves `/api/v1/public/{slug}/...` for the workshop with that slug (ADR 0004): finds it, answers 404
/// `workshop-not-found` for a missing, unknown or malformed slug or a workshop that is not open (the same answer for
/// all of them), and runs the rest of the request inside its [TenantScope]. No request on the public path goes on
/// without a scope. The scope is bound before any transaction opens, so every connection of the request carries the
/// workshop and row-level security filters as for a signed-in user.
final class PublicWorkshopFilter extends OncePerRequestFilter {

    private final WorkshopDirectory directory;
    private final ProblemResponses problems;

    PublicWorkshopFilter(WorkshopDirectory directory, ProblemResponses problems) {
        this.directory = directory;
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PublicPaths.isPublic(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> slug = PublicPaths.slug(request);
        Optional<PublicWorkshop> workshop = slug.flatMap(directory::publicWorkshop);
        if (workshop.isEmpty()) {
            problems.workshopNotFound(request, response, slug.orElse(""));
            return;
        }
        PublicWorkshop w = workshop.get();
        request.setAttribute(PublicPaths.WORKSHOP_ATTRIBUTE,
                new PublicWorkshopRef(w.tenantId(), w.name(), w.timeZone(), w.country(), w.booking(), w.shop()));
        try {
            TenantScope.callAs(w.tenantId(), () -> {
                chain.doFilter(request, response);
                return null;
            });
        } catch (IOException | ServletException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }
}
