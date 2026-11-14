package com.asp.platform.config.web;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.asp.platform.shared.StatusTokens;
import com.github.benmanes.caffeine.cache.Ticker;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// Slows down guessing on the sign-in endpoints (`app.auth.rate-limit.*`):
/// - every request to `POST /api/v1/auth/login`, `POST /api/v1/auth/activate` and `GET /api/v1/auth/activation` counts
///   against its client address (10 a minute by default; IPv6 by its /64). Successful requests count too;
/// - a login that fails (401) counts against the e-mail it submitted, 5 in 15 minutes by default, from any address.
///   The key is the SHA-256 of the trimmed, lower-case e-mail, and is counted whether or not an account exists, so a
///   429 never tells which e-mails are registered. The e-mail itself is not kept or logged. Once an e-mail has used up
///   its failures, further logins for it answer 429 until the window ends, also with the right password; that is the
///   price of not telling who exists (someone who knows an e-mail can lock it out for 15 minutes).
///
/// Over a limit: 429 `too-many-requests` with `Retry-After`, logged once per address and window. In memory, per
/// instance, like the public page limits.
final class AuthLimiter extends OncePerRequestFilter {

    static final String LOGIN = "/api/v1/auth/login";
    static final String ACTIVATE = "/api/v1/auth/activate";
    static final String ACTIVATION_PREVIEW = "/api/v1/auth/activation";
    static final String PAYLOAD_TOO_LARGE = "payload-too-large";
    static final String UNSUPPORTED_CHARSET = "unsupported-charset";
    /// A login body is a few hundred bytes; a bigger one is not parsed for the e-mail (the address limit still applies).
    private static final int MAX_PARSED_BODY = 8 * 1024;

    private final FixedWindowLimit attempts;
    private final FixedWindowLimit failures;
    private final ProblemResponses problems;
    private final JsonMapper json;

    AuthLimiter(AuthRateLimitProperties properties, ProblemResponses problems, JsonMapper json, Ticker ticker) {
        this.attempts = new FixedWindowLimit(properties.attempts(), ticker);
        this.failures = new FixedWindowLimit(properties.failures(), ticker);
        this.problems = problems;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = path(request);
        String method = request.getMethod();
        boolean login = "POST".equals(method) && (LOGIN.equals(path) || ACTIVATE.equals(path));
        boolean preview = "GET".equals(method) && ACTIVATION_PREVIEW.equals(path);
        return !(login || preview);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        FixedWindowLimit.Outcome outcome = attempts.acquire(ClientAddresses.key(request.getRemoteAddr()));
        if (outcome.refused()) {
            problems.tooManyRequests(request, response, "auth", outcome);
            return;
        }
        if (!LOGIN.equals(path(request))) {
            chain.doFilter(request, response);
            return;
        }
        if (!utf8OrUnspecified(request.getContentType())) {
            // The limiter reads the e-mail as UTF-8; the controller decodes by the declared charset. Equal, or refused.
            problems.write(request, response, HttpStatus.UNSUPPORTED_MEDIA_TYPE, UNSUPPORTED_CHARSET,
                    "Only UTF-8 is accepted");
            return;
        }
        byte[] raw = request.getInputStream().readNBytes(MAX_PARSED_BODY + 1);
        if (raw.length > MAX_PARSED_BODY) {
            // Refused without being parsed: it could slip past the per-e-mail limit, and a login body is never this big.
            problems.write(request, response, HttpStatus.CONTENT_TOO_LARGE, PAYLOAD_TOO_LARGE,
                    "The request body is too large");
            return;
        }
        String emailKey = emailKey(raw);
        if (emailKey == null) {
            // Unreadable, not JSON or without an e-mail: invalid for the controller too, and it could skip the limit.
            problems.write(request, response, HttpStatus.BAD_REQUEST, "validation", "Some fields are not valid",
                    Map.of("email", "must not be blank"));
            return;
        }
        FixedWindowLimit.Outcome locked = failures.peek(emailKey);
        if (locked.refused()) {
            problems.tooManyRequests(request, response, "auth", locked);
            return;
        }
        chain.doFilter(new ReplayableBody(request, raw), response);
        if (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
            failures.acquire(emailKey);
        }
    }

    /// True when the Content-Type declares no charset or UTF-8 (JSON is UTF-8 by default).
    // Relies on Spring Boot forcing UTF-8 request decoding (`server.servlet.encoding.force-request`, on by default), so
    // a charset-less body is UTF-8 for the controller as it is for this filter (AuthLimiterEncodingTests guards it).
    static boolean utf8OrUnspecified(@Nullable String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return true;
        }
        try {
            var charset = MediaType.parseMediaType(contentType).getCharset();
            return charset == null || StandardCharsets.UTF_8.equals(charset);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String path(HttpServletRequest request) {
        return request.getServletPath() + Objects.toString(request.getPathInfo(), "");
    }

    /// SHA-256 of the normalised e-mail in the JSON body, or null when there is none to read.
    private @Nullable String emailKey(byte[] head) {
        if (head.length == 0) {
            return null;
        }
        try {
            JsonNode email = json.readTree(new String(head, StandardCharsets.UTF_8)).path("email");
            if (!email.isString() || email.asString().isBlank()) {
                return null;
            }
            return StatusTokens.hash(email.asString().trim().toLowerCase(Locale.ROOT));
        } catch (JacksonException e) {
            return null;
        }
    }

    /// The request with its body readable again: the filter has read all of it to find the e-mail.
    private static final class ReplayableBody extends HttpServletRequestWrapper {

        private final byte[] body;

        private ReplayableBody(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream source = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return source.read();
                }

                @Override
                public int read(byte[] buffer, int offset, int length) {
                    return source.read(buffer, offset, length);
                }

                @Override
                public boolean isFinished() {
                    return source.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Blocking reads only");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            // The filter lets only an absent or UTF-8 charset through, so the body is UTF-8.
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
