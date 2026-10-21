package com.asp.platform.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

import com.asp.platform.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;

/// Changing a team member locks the whole team. When another transaction holds those locks for longer
/// than the 3 s lock timeout, the request gives up with 409 `try-again` instead of hanging.
class TeamLockTimeoutTests extends ApiTestSupport {

    @Autowired
    private DataSource dataSource;

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void should_answer_try_again_when_the_team_stays_locked_past_the_timeout() throws Exception {
        String owner = signup("Oficina Bloqueada");
        String deskId = invite(owner, "FRONT_DESK", uniqueEmail("desk"));
        String users = call(get("/api/v1/users"), owner).andReturn().getResponse().getContentAsString();
        UUID tenantId = UUID.fromString(JsonPath.read(users, "$[0].tenantId"));

        try (Connection other = dataSource.getConnection()) {
            other.setAutoCommit(false);
            lockTeam(other, tenantId);

            long started = System.nanoTime();
            call(put("/api/v1/users/" + deskId).content("{\"role\":\"MECHANIC\",\"active\":true}"), owner)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.type").value("/problems/try-again"))
                    .andExpect(jsonPath("$.code").value("try-again"));
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(15));

            other.rollback();
        }

        // Once the lock is released the same change goes through.
        call(put("/api/v1/users/" + deskId).content("{\"role\":\"MECHANIC\",\"active\":true}"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MECHANIC"));
    }

    private static void lockTeam(Connection connection, UUID tenantId) throws Exception {
        try (PreparedStatement lock = connection.prepareStatement(
                "select id from app_user where tenant_id = ? order by id for update")) {
            lock.setObject(1, tenantId);
            try (ResultSet rows = lock.executeQuery()) {
                assertThat(rows.next()).isTrue();
            }
        }
    }
}
