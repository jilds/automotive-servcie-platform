package com.asp;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Starts one PostgreSQL container for all integration tests, with the same two roles as production. */
@SpringBootTest
public abstract class PostgresTestBase {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withInitScript("db/init-roles.sql");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "asp_app");
        registry.add("spring.datasource.password", () -> "asp_app");
        registry.add("spring.flyway.user", () -> "asp_owner");
        registry.add("spring.flyway.password", () -> "asp_owner");
    }
}
