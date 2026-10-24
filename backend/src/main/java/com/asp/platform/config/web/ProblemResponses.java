package com.asp.platform.config.web;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import tools.jackson.databind.json.JsonMapper;

/// Writes an RFC 9457 problem from a servlet filter, which runs before Spring MVC and its exception handlers, in the
/// same shape as [GlobalExceptionHandler]: `type` `/problems/<code>`, `title`, `status`, `detail`, `instance`, `code`.
final class ProblemResponses {

    static final String TOO_MANY_REQUESTS = "too-many-requests";
    static final String WORKSHOP_NOT_FOUND = "workshop-not-found";

    private static final Logger log = LoggerFactory.getLogger(ProblemResponses.class);
    private static final int MAX_LOGGED_SLUG = 64;

    private final JsonMapper json;

    ProblemResponses(JsonMapper json) {
        this.json = json;
    }

    /// 429 `too-many-requests` with `Retry-After` in seconds. Logged with the slug and method only, never the
    /// client's address.
    void tooManyRequests(HttpServletRequest request, HttpServletResponse response, String slug,
            long retryAfterSeconds) throws IOException {
        log.info("Public request refused: too many requests slug={} method={}", loggable(slug), request.getMethod());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        write(request, response, HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_REQUESTS,
                "Too many requests; try again later");
    }

    /// 404 `workshop-not-found`, the same for an unknown, malformed or closed slug. Logged like [#tooManyRequests].
    void workshopNotFound(HttpServletRequest request, HttpServletResponse response, String slug) throws IOException {
        log.info("Public request refused: workshop not found slug={} method={}", loggable(slug), request.getMethod());
        write(request, response, HttpStatus.NOT_FOUND, WORKSHOP_NOT_FOUND, "Workshop not found");
    }

    void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code,
            String detail) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "/problems/" + code);
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());
        body.put("code", code);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json.writeValueAsString(body));
    }

    /// The slug comes from the address bar: keep it short and printable so it cannot forge log lines.
    static String loggable(String slug) {
        String shortened = slug.length() > MAX_LOGGED_SLUG ? slug.substring(0, MAX_LOGGED_SLUG) : slug;
        return shortened.replaceAll("[^A-Za-z0-9._~-]", "?");
    }
}
