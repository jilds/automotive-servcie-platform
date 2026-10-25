package com.asp.platform.config.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.asp.platform.identity.TokenClaims;
import com.jayway.jsonpath.JsonPath;
import com.zaxxer.hikari.HikariDataSource;

/// The public pages on a real Tomcat with a pool of one connection (ADR 0004).
///
/// - Tomcat decodes and normalises the path before Spring Security and the filter mapping see it; MockMvc does not,
///   so the encoded variant of a public path is only tested here.
/// - With one connection every checkout reuses the same physical connection: a public request's workshop must never
///   stay on it for the next checkout.
///
/// Not an `ApiTestSupport`: an embedded server creates the servlet filters, and through them the data source, before
/// `TestcontainersConfiguration`'s property registrar runs. The container is started here and its properties are set
/// before the context starts, so this test can never reach another database.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.hikari.maximum-pool-size=1")
@AutoConfigureMockMvc
class PublicWorkshopServerTests {

    private static final String CONNECTION_TENANT = "select current_setting('app.tenant_id', true)";
    private static final String APP_ROLE = "asp_app";
    private static final String OWNER_ROLE = "asp_owner";

    /// Stopped by Testcontainers when the JVM exits.
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.6").withInitScript("db/init-roles.sql");

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    DataSource dataSource;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_ROLE);
        registry.add("spring.datasource.password", () -> APP_ROLE);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", () -> OWNER_ROLE);
        registry.add("spring.flyway.password", () -> OWNER_ROLE);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_never_serve_an_encoded_public_path_without_the_scope_or_the_rate_limit() throws Exception {
        Workshop a = workshop("Oficina Servidor Codificada");
        String encoded = "/api/v1/%70ublic/" + a.slug() + "/probe";

        HttpResponse<String> first = get(encoded);
        if (first.statusCode() == 400) {
            // Rejected by the firewall: fine, it never reached a controller.
            return;
        }
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(first.body(), "$.scope")).isEqualTo(a.id());
        assertThat((String) JsonPath.read(first.body(), "$.connectionTenant")).isEqualTo(a.id());

        // The encoded and the plain spelling share one counter: 20 reads a minute in the test configuration.
        for (int i = 1; i < 20; i++) {
            assertThat(get(i % 2 == 0 ? encoded : "/api/v1/public/" + a.slug() + "/probe").statusCode())
                    .isEqualTo(200);
        }
        HttpResponse<String> refused = get(encoded);
        assertThat(refused.statusCode()).isEqualTo(429);
        assertThat(refused.headers().firstValue("Retry-After")).isPresent();
    }

    @Test
    void should_answer_workshop_not_found_on_the_public_root() throws Exception {
        for (String path : new String[] {"/api/v1/public", "/api/v1/public/", "/api/v1/%70ublic"}) {
            HttpResponse<String> response = get(path);
            if (response.statusCode() == 400) {
                continue;
            }
            assertThat(response.statusCode()).as(path).isEqualTo(404);
            assertThat((String) JsonPath.read(response.body(), "$.code")).as(path).isEqualTo("workshop-not-found");
        }
    }

    @Test
    void should_rebind_the_single_pooled_connection_on_every_checkout() throws Exception {
        assertThat(dataSource.unwrap(HikariDataSource.class).getMaximumPoolSize()).isEqualTo(1);
        Workshop a = workshop("Oficina Servidor Pool A");
        Workshop b = workshop("Oficina Servidor Pool B");

        HttpResponse<String> publicCall = get("/api/v1/public/" + a.slug() + "/probe");
        assertThat(publicCall.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(publicCall.body(), "$.connectionTenant")).isEqualTo(a.id());
        int backendPid = JsonPath.read(publicCall.body(), "$.backendPid");

        // Next checkout of the same physical connection: no token and no scope, so no workshop.
        assertThat(backendPid()).isEqualTo(backendPid);
        assertThat(jdbc.sql(CONNECTION_TENANT).query(String.class).single()).isEmpty();

        // And with B's token: B, not A.
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwtOf(b), List.of()));
        assertThat(backendPid()).isEqualTo(backendPid);
        assertThat(jdbc.sql(CONNECTION_TENANT).query(String.class).single()).isEqualTo(b.id());
    }

    private int backendPid() {
        return jdbc.sql("select pg_backend_pid()").query(Integer.class).single();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /// Registered by the platform admin of `src/test/resources/config/application.yml`; the owner is not activated.
    private Workshop workshop(String name) throws Exception {
        String admin = JsonPath.read(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"platform-admin@asp.test\",\"password\":\"platform-admin-test-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.accessToken");
        String body = mvc.perform(post("/api/v1/admin/workshops").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + admin)
                .content("""
                        {"name":"%s","country":"PT","timezone":"Europe/Lisbon","plan":"PRO",
                         "ownerName":"Dono","ownerEmail":"dono-%s@asp.test"}""".formatted(name, UUID.randomUUID())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Workshop(JsonPath.read(body, "$.workshop.id"), JsonPath.read(body, "$.workshop.slug"));
    }

    private static Jwt jwtOf(Workshop workshop) {
        return Jwt.withTokenValue("token").header("alg", "HS256").subject(UUID.randomUUID().toString())
                .claim(TokenClaims.TENANT_ID, workshop.id())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    private record Workshop(String id, String slug) {
    }
}
