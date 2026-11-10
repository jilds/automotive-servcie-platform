package com.asp.platform.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import com.asp.platform.ApiTestSupport;

/// The app connections stop a statement after 30 s and an idle transaction after 60 s; the migration role is not
/// limited, and Flyway says so explicitly.
class DatabaseTimeoutsTests extends ApiTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Environment environment;

    @Value("${spring.datasource.url}")
    String url;

    @Test
    void should_limit_the_app_connections() {
        assertThat(jdbc.queryForObject("show statement_timeout", String.class)).isEqualTo("30s");
        assertThat(jdbc.queryForObject("show idle_in_transaction_session_timeout", String.class)).isEqualTo("1min");
    }

    @Test
    void should_not_limit_the_migration_connection_and_say_so_in_the_flyway_settings() throws Exception {
        try (var connection = DriverManager.getConnection(url, "asp_owner", "asp_owner");
                var statement = connection.createStatement();
                var rs = statement.executeQuery("show statement_timeout")) {
            rs.next();
            assertThat(rs.getString(1)).isEqualTo("0");
        }
        assertThat(environment.getProperty("spring.flyway.init-sqls")).isNotNull()
                .contains("statement_timeout");
    }
}
