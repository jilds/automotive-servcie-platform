package com.asp.platform.config.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.github.benmanes.caffeine.cache.Ticker;

import tools.jackson.databind.json.JsonMapper;

/// The sign-in limits on a fake clock: per address, and per hashed e-mail for failed logins.
class AuthLimiterTests {

    private final AtomicLong nanos = new AtomicLong(1_000_000_000L);
    private final Ticker ticker = nanos::get;
    private final JsonMapper json = JsonMapper.builder().build();
    private final AuthLimiter limiter = new AuthLimiter(
            new AuthRateLimitProperties(new PublicRateLimitProperties.Rule(3, Duration.ofMinutes(1)),
                    new PublicRateLimitProperties.Rule(2, Duration.ofMinutes(15))),
            new ProblemResponses(json), json, ticker);

    @Test
    void should_refuse_the_attempt_after_the_address_limit_with_retry_after_and_a_problem() throws Exception {
        assertThat(login("10.0.0.1", "a@x.test", 200).getStatus()).isEqualTo(200);
        assertThat(login("10.0.0.1", "b@x.test", 200).getStatus()).isEqualTo(200);
        assertThat(login("10.0.0.1", "c@x.test", 200).getStatus()).isEqualTo(200);

        MockHttpServletResponse refused = login("10.0.0.1", "d@x.test", 200);

        assertThat(refused.getStatus()).isEqualTo(429);
        assertThat(refused.getHeader("Retry-After")).isEqualTo("60");
        assertThat(refused.getContentType()).startsWith("application/problem+json");
        assertThat(refused.getContentAsString()).contains("\"code\":\"too-many-requests\"");
        assertThat(login("10.0.0.2", "d@x.test", 200).getStatus()).isEqualTo(200);

        nanos.addAndGet(Duration.ofSeconds(61).toNanos());
        assertThat(login("10.0.0.1", "d@x.test", 200).getStatus()).isEqualTo(200);
    }

    @Test
    void should_lock_one_email_after_its_failures_without_locking_others_and_whatever_the_address() throws Exception {
        assertThat(login("10.0.0.1", "Victim@x.test", 401).getStatus()).isEqualTo(401);
        assertThat(login("10.0.0.2", " victim@X.test ", 401).getStatus()).isEqualTo(401);

        MockHttpServletResponse locked = login("10.0.0.3", "victim@x.test", 200);
        assertThat(locked.getStatus()).isEqualTo(429);
        assertThat(locked.getHeader("Retry-After")).isEqualTo("900");
        assertThat(login("10.0.0.3", "someone-else@x.test", 200).getStatus()).isEqualTo(200);

        nanos.addAndGet(Duration.ofMinutes(15).plusSeconds(1).toNanos());
        assertThat(login("10.0.0.3", "victim@x.test", 200).getStatus()).isEqualTo(200);
    }

    @Test
    void should_lock_an_email_the_same_way_whether_or_not_the_account_exists() throws Exception {
        // The filter sees only the status of the answer: an unknown and a known e-mail both fail with 401 and both lock.
        for (String email : new String[] {"known@x.test", "unknown@x.test"}) {
            login("10.0.1.1", email, 401);
            login("10.0.1.2", email, 401);
            assertThat(login("10.0.1.3", email, 200).getStatus()).as(email).isEqualTo(429);
        }
    }

    @Test
    void should_not_count_successful_or_other_failed_logins_against_the_email() throws Exception {
        login("10.0.2.1", "ok@x.test", 200);
        login("10.0.2.2", "ok@x.test", 200);
        login("10.0.2.3", "ok@x.test", 403);
        login("10.0.2.4", "ok@x.test", 400);

        assertThat(login("10.0.2.5", "ok@x.test", 200).getStatus()).isEqualTo(200);
    }

    @Test
    void should_hand_the_body_on_to_the_controller_unchanged() throws Exception {
        String body = "{\"email\":\"a@x.test\",\"password\":\"secret-password\"}";
        MockHttpServletRequest request = request("POST", AuthLimiter.LOGIN, "10.0.3.1", body);
        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8));

        limiter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(seen.get()).isEqualTo(body);
    }

    @Test
    void should_answer_413_to_a_login_body_too_big_to_read_the_email_from_without_calling_the_controller()
            throws Exception {
        String body = "{\"email\":\"a@x.test\",\"password\":\"" + "p".repeat(20_000) + "\"}";
        MockHttpServletRequest request = request("POST", AuthLimiter.LOGIN, "10.0.3.2", body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set("called");

        limiter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("\"code\":\"payload-too-large\"");
        assertThat(seen.get()).isNull();
    }

    @Test
    void should_answer_415_and_never_reach_the_controller_for_a_login_in_another_charset() throws Exception {
        // EBCDIC would make the limiter's parse fail while Spring decodes it by the declared charset.
        int host = 10;
        for (String charset : new String[] {"IBM037", "ISO-8859-1", "UTF-16"}) {
            for (int attempt = 0; attempt < 4; attempt++) {
                MockHttpServletRequest request = request("POST", AuthLimiter.LOGIN, "10.0.3." + host++,
                        "{\"email\":\"a@x.test\",\"password\":\"wrong-password-1\"}");
                request.setContentType("application/json;charset=" + charset);
                MockHttpServletResponse response = new MockHttpServletResponse();
                AtomicReference<String> seen = new AtomicReference<>();

                limiter.doFilter(request, response, (req, res) -> seen.set("called"));

                assertThat(response.getStatus()).as(charset).isEqualTo(415);
                assertThat(response.getContentAsString()).contains("\"code\":\"unsupported-charset\"");
                assertThat(seen.get()).as(charset).isNull();
            }
        }
    }

    @Test
    void should_accept_only_an_absent_or_utf8_charset() {
        assertThat(AuthLimiter.utf8OrUnspecified(null)).isTrue();
        assertThat(AuthLimiter.utf8OrUnspecified("application/json")).isTrue();
        assertThat(AuthLimiter.utf8OrUnspecified("application/json;charset=UTF-8")).isTrue();
        assertThat(AuthLimiter.utf8OrUnspecified("application/json; charset=\"utf-8\"")).isTrue();
        assertThat(AuthLimiter.utf8OrUnspecified("application/json;charset=IBM037")).isFalse();
        assertThat(AuthLimiter.utf8OrUnspecified("application/json;charset=nonsense-1")).isFalse();
        assertThat(AuthLimiter.utf8OrUnspecified("application/json;charset=")).isFalse();
        assertThat(AuthLimiter.utf8OrUnspecified("not a media type")).isFalse();
    }

    @Test
    void should_refuse_a_login_body_without_a_readable_email_with_400_and_not_call_the_controller() throws Exception {
        int host = 100;
        for (String body : new String[] {"", "not json", "{}", "{\"email\":\"  \"}", "{\"email\":5}", "[1]"}) {
            MockHttpServletRequest request = request("POST", AuthLimiter.LOGIN, "10.0.3." + host++, body);
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicReference<String> seen = new AtomicReference<>();

            limiter.doFilter(request, response, (req, res) -> seen.set("called"));

            assertThat(response.getStatus()).as(body).isEqualTo(400);
            assertThat(response.getContentAsString()).contains("\"code\":\"validation\"").contains("\"email\"");
            assertThat(seen.get()).as(body).isNull();
        }
    }

    @Test
    void should_still_serve_and_count_a_login_with_an_explicit_utf8_charset() throws Exception {
        for (String contentType : new String[] {"application/json; charset=utf-8", "application/json;charset=\"UTF-8\""}) {
            MockHttpServletRequest request = request("POST", AuthLimiter.LOGIN, "10.0.3.40",
                    "{\"email\":\"Utf8@x.test\",\"password\":\"wrong-password-1\"}");
            request.setContentType(contentType);
            MockHttpServletResponse response = new MockHttpServletResponse();

            limiter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(401));

            assertThat(response.getStatus()).as(contentType).isEqualTo(401);
        }
        // Both failures were counted for the same e-mail: the limit of 2 is used up.
        assertThat(login("10.0.3.41", "utf8@x.test", 200).getStatus()).isEqualTo(429);
    }

    @Test
    void should_limit_activation_and_its_preview_per_address_and_ignore_other_paths() throws Exception {
        assertThat(get("10.0.4.1", AuthLimiter.ACTIVATION_PREVIEW)).isEqualTo(200);
        assertThat(post("10.0.4.1", AuthLimiter.ACTIVATE)).isEqualTo(200);
        assertThat(get("10.0.4.1", AuthLimiter.ACTIVATION_PREVIEW)).isEqualTo(200);
        assertThat(get("10.0.4.1", AuthLimiter.ACTIVATION_PREVIEW)).isEqualTo(429);
        assertThat(post("10.0.4.1", AuthLimiter.ACTIVATE)).isEqualTo(429);

        assertThat(limiter.shouldNotFilter(request("GET", "/api/v1/branding/abc", "10.0.4.1", ""))).isTrue();
        assertThat(limiter.shouldNotFilter(request("POST", AuthLimiter.ACTIVATION_PREVIEW, "10.0.4.1", ""))).isTrue();
    }

    private MockHttpServletResponse login(String address, String email, int answerStatus) throws Exception {
        MockHttpServletRequest request = request("POST", AuthLimiter.LOGIN, address,
                "{\"email\":\"%s\",\"password\":\"whatever-123\"}".formatted(email));
        MockHttpServletResponse response = new MockHttpServletResponse();
        limiter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(answerStatus));
        return response;
    }

    private int get(String address, String path) throws Exception {
        return status(request("GET", path, address, ""));
    }

    private int post(String address, String path) throws Exception {
        return status(request("POST", path, address, "{}"));
    }

    private int status(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        limiter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
        return response.getStatus();
    }

    private static MockHttpServletRequest request(String method, String path, String address, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.setRemoteAddr(address);
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
