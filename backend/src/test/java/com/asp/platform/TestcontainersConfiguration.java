package com.asp.platform;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

/// A throw-away PostgreSQL with the same two roles as production.
///
/// No `@ServiceConnection` on purpose: it would make the app log in as the container's superuser, which
/// bypasses row-level security. The app connects as `asp_app` and Flyway as `asp_owner`, as in production.
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final String APP_ROLE = "asp_app";
    private static final String OWNER_ROLE = "asp_owner";

    @Bean
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:18.6").withInitScript("db/init-roles.sql");
    }

    @Bean
    DynamicPropertyRegistrar databaseProperties(PostgreSQLContainer postgres) {
        return registry -> {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", () -> APP_ROLE);
            registry.add("spring.datasource.password", () -> APP_ROLE);
            registry.add("spring.flyway.user", () -> OWNER_ROLE);
            registry.add("spring.flyway.password", () -> OWNER_ROLE);
        };
    }
}
