package com.asp.platform.config.prod;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/// The `prod` profile on a real Tomcat with its own database container. Everything `application-prod.yml` takes from
/// the environment is set here, so a missing setting would stop the context, as it stops a production start.
///
/// Not an `ApiTestSupport`: like `PublicWorkshopServerTests`, the container starts and its properties are set before
/// the context starts, so this test can never reach another database.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.security.jwt-secret=prod-profile-test-secret-not-the-dev-one-0123456789",
        "management.server.port=0", "app.public-url=https://app.asp.test"})
@ActiveProfiles("prod")
abstract class ProdProfileServerTestSupport {

    private static final String APP_ROLE = "asp_app";
    private static final String OWNER_ROLE = "asp_owner";

    /// Shared by the subclasses (one start), stopped by Testcontainers when the JVM exits.
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.6").withInitScript("db/init-roles.sql");

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @LocalManagementPort
    int managementPort;

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

    HttpResponse<String> get(int serverPort, String path, Map<String, String> headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + serverPort + path));
        headers.forEach(request::header);
        return http.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> get(String path, Map<String, String> headers) throws IOException, InterruptedException {
        return get(port, path, headers);
    }

    HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return get(path, Map.of());
    }
}
