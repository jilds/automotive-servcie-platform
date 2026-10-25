package com.asp.platform.config.web;

import java.util.Objects;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

/// The public workshop pages' API: `/api/v1/public/{slug}/...`, no token needed (ADR 0004).
///
/// Paths are read from `getServletPath() + getPathInfo()`: decoded and normalised by the container, the same path
/// Spring Security's chain matcher and the servlet filter mapping see. The raw `getRequestURI()` would let an encoded
/// variant such as `/api/v1/%70ublic/...` reach the public chain without being recognised here.
final class PublicPaths {

    static final String ROOT = "/api/v1/public";
    static final String PREFIX = ROOT + "/";
    /// Servlet URL pattern the public filters are registered for; it also matches [#ROOT] itself.
    static final String URL_PATTERN = PREFIX + "*";
    /// Request attribute holding the [com.asp.platform.shared.PublicWorkshopRef] of the request.
    static final String WORKSHOP_ATTRIBUTE = PublicPaths.class.getName() + ".workshop";

    private PublicPaths() {
    }

    /// True for every path the public security chain (`/api/v1/public/**`) serves, with or without a slug.
    static boolean isPublic(HttpServletRequest request) {
        String path = path(request);
        return path.equals(ROOT) || path.startsWith(PREFIX);
    }

    /// The `{slug}` segment, or empty when the path has none.
    static Optional<String> slug(HttpServletRequest request) {
        String path = path(request);
        if (!path.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String rest = path.substring(PREFIX.length());
        int end = rest.indexOf('/');
        String slug = end < 0 ? rest : rest.substring(0, end);
        return slug.isEmpty() ? Optional.empty() : Optional.of(slug);
    }

    private static String path(HttpServletRequest request) {
        return request.getServletPath() + Objects.toString(request.getPathInfo(), "");
    }
}
