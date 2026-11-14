package com.asp.platform.identity.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.asp.platform.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;

/// 429 `too-many-requests` on the sign-in endpoints, through the real filter chain: 10 requests a minute per
/// address, 3 failed logins per submitted e-mail (small limits, set for this class).
@TestPropertySource(properties = {
        "app.auth.rate-limit.attempts.limit=10",
        "app.auth.rate-limit.attempts.window=1m",
        "app.auth.rate-limit.failures.limit=3",
        "app.auth.rate-limit.failures.window=15m"})
class AuthRateLimitApiTests extends ApiTestSupport {

    private String address;

    @BeforeEach
    void newClientAddress() {
        address = randomAddress();
    }

    @Test
    void should_answer_429_with_retry_after_on_the_eleventh_login_attempt_of_an_address() throws Exception {
        for (int attempt = 1; attempt <= 10; attempt++) {
            attemptLogin(address, uniqueEmail("a" + attempt), "wrong-password-1").andExpect(status().isUnauthorized());
        }

        attemptLogin(address, uniqueEmail("a11"), "wrong-password-1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("too-many-requests"))
                .andExpect(jsonPath("$.type").value("/problems/too-many-requests"));

        // Another address is not affected.
        attemptLogin(randomAddress(), uniqueEmail("b"), "wrong-password-1").andExpect(status().isUnauthorized());
    }

    @Test
    void should_lock_a_known_and_an_unknown_email_alike_after_three_failures_and_leave_other_emails_alone()
            throws Exception {
        String ownerEmail = uniqueEmail("locked");
        activate(JsonPath.read(provisionWorkshop("Oficina Bloqueio", ownerEmail)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.activationPath"), "Owner");
        String unknownEmail = uniqueEmail("ghost");

        for (String email : new String[] {ownerEmail, unknownEmail}) {
            for (int failure = 0; failure < 3; failure++) {
                attemptLogin(randomAddress(), email, "wrong-password-1").andExpect(status().isUnauthorized());
            }
            // The right password is refused too while locked: the answer is the same for both e-mails.
            attemptLogin(randomAddress(), email, PASSWORD)
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(jsonPath("$.code").value("too-many-requests"));
        }

        // Another e-mail and the platform admin are not locked.
        attemptLogin(randomAddress(), PLATFORM_ADMIN_EMAIL, PLATFORM_ADMIN_PASSWORD).andExpect(status().isOk());
        attemptLogin(randomAddress(), uniqueEmail("other"), "wrong-password-1").andExpect(status().isUnauthorized());
    }

    @Test
    void should_refuse_a_login_with_an_oversized_email_or_password_and_a_padded_body() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).with(from(address))
                .content("{\"email\":\"%s@x.test\",\"password\":\"%s\"}".formatted("a".repeat(250), "p".repeat(129))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());

        // Padding cannot slip a login past the per-e-mail limit: a body this big never reaches the controller.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).with(from(address))
                .content("{\"email\":\"a@x.test\",\"password\":\"wrong-password-1\"," + " ".repeat(9_000) + "\"x\":1}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("payload-too-large"));
    }

    @Test
    void should_not_count_successful_logins_as_failures()throws Exception {
        String ownerEmail = uniqueEmail("steady");
        activate(JsonPath.read(provisionWorkshop("Oficina Estavel", ownerEmail)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.activationPath"), "Owner");

        for (int login = 0; login < 5; login++) {
            attemptLogin(randomAddress(), ownerEmail, PASSWORD).andExpect(status().isOk());
        }
    }

    @Test
    void should_limit_the_activation_preview_and_activation_per_address() throws Exception {
        for (int attempt = 1; attempt <= 10; attempt++) {
            mvc.perform(get("/api/v1/auth/activation").param("token", "unknown-token").with(from(address)))
                    .andExpect(status().is(org.hamcrest.Matchers.lessThan(500)))
                    .andExpect(result -> org.assertj.core.api.Assertions
                            .assertThat(result.getResponse().getStatus()).isNotEqualTo(429));
        }

        mvc.perform(get("/api/v1/auth/activation").param("token", "unknown-token").with(from(address)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("too-many-requests"));
        mvc.perform(post("/api/v1/auth/activate").contentType(MediaType.APPLICATION_JSON).with(from(address))
                .content("{\"token\":\"unknown-token\",\"name\":\"X\",\"password\":\"password-1234\"}"))
                .andExpect(status().isTooManyRequests());
    }

    private ResultActions attemptLogin(String clientAddress, String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .with(from(clientAddress))
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    /// The helpers of the base class would all come from 127.0.0.1 and use up its 10 a minute: they get addresses of
    /// their own, like every test here.
    @Override
    protected String login(String email, String password) throws Exception {
        return JsonPath.read(attemptLogin(randomAddress(), email, password)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    @Override
    protected String activate(String activationPath, String name) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/auth/activate").contentType(MediaType.APPLICATION_JSON)
                .with(from(randomAddress()))
                .content("{\"token\":\"%s\",\"name\":\"%s\",\"password\":\"%s\"}"
                        .formatted(tokenOf(activationPath), name, PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    private static final AtomicInteger NEXT_ADDRESS = new AtomicInteger();

    /// A client address no other call of this class has used: 10.9.x.y from a counter.
    private static String randomAddress() {
        int n = NEXT_ADDRESS.getAndIncrement();
        return "10.9.%d.%d".formatted(n / 250 % 256, n % 250 + 1);
    }

    private static RequestPostProcessor from(String clientAddress) {
        return request -> {
            request.setRemoteAddr(clientAddress);
            return request;
        };
    }
}
