package com.asp.platform.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.asp.platform.ApiTestSupport;
import com.asp.platform.catalogue.domain.ServiceDetails;
import com.asp.platform.catalogue.domain.ServiceItem;
import com.asp.platform.catalogue.domain.ServiceItemRepository;
import com.asp.platform.catalogue.domain.ServicePricing;
import com.asp.platform.identity.domain.PlatformAdmin;
import com.asp.platform.identity.domain.PlatformAdminRepository;

/// [Upserts] through two real adapters: the service catalogue (an entity with `@Version`, saved with
/// [Upserts#upsert]) and the platform admins (an entity without `@Version`, saved with [Upserts#upsertWithoutFlush]).
/// No test entity: the shared Spring context validates the schema against the mapped entities.
class UpsertsTests extends ApiTestSupport {

    @Autowired
    private ServiceItemRepository services;

    @Autowired
    private PlatformAdminRepository admins;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_store_a_new_versioned_entity_with_version_0_before_the_commit() throws Exception {
        signIn(signup("Oficina Upsert A"));
        UUID id = UUID.randomUUID();

        inTransaction(() -> {
            ServiceItem saved = services.save(new ServiceItem(id, details("Revisão")));

            assertThat(saved.version()).isZero();
            assertThat(rows("service_item", id)).as("flushed at once").isOne();
            return saved;
        });
    }

    @Test
    void should_update_a_stored_versioned_entity_and_return_the_next_version() throws Exception {
        signIn(signup("Oficina Upsert B"));
        UUID id = UUID.randomUUID();
        inTransaction(() -> services.save(new ServiceItem(id, details("Revisão"))));

        ServiceItem updated = inTransaction(() -> {
            ServiceItem item = services.findById(id).orElseThrow();
            item.update(details("Revisão completa"));
            return services.save(item);
        });

        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.details().name()).isEqualTo("Revisão completa");
        ServiceItem stored = inTransaction(() -> services.findById(id).orElseThrow());
        assertThat(stored.version()).isEqualTo(1);
        assertThat(stored.details().name()).isEqualTo("Revisão completa");
    }

    @Test
    void should_insert_and_update_a_versionless_entity_at_commit() {
        UUID id = UUID.randomUUID();
        String email = uniqueEmail("upsert-admin");

        PlatformAdmin inserted = inTransaction(() -> {
            PlatformAdmin saved = admins.save(PlatformAdmin.create(id, "Ana", email, "not-a-real-hash", Instant.now()));
            assertThat(rows("platform_admin", id)).as("no flush before the commit").isZero();
            return saved;
        });
        assertThat(inserted.name()).isEqualTo("Ana");
        assertThat(rows("platform_admin", id)).isOne();

        PlatformAdmin renamed = inTransaction(() -> {
            PlatformAdmin admin = admins.findById(id).orElseThrow();
            return admins.save(new PlatformAdmin(id, "Ana Lima", admin.email(), admin.passwordHash(), false,
                    admin.createdAt()));
        });

        assertThat(renamed.name()).isEqualTo("Ana Lima");
        PlatformAdmin stored = inTransaction(() -> admins.findById(id).orElseThrow());
        assertThat(stored.name()).isEqualTo("Ana Lima");
        assertThat(stored.active()).isFalse();
    }

    /// Plain JDBC on the transaction's connection: sees only what Hibernate has already written.
    private int rows(String table, UUID id) {
        Integer count = jdbc.queryForObject("select count(*) from " + table + " where id = ?", Integer.class, id);
        return count == null ? 0 : count;
    }

    /// Binds the workshop for row-level security the way a request with this token would.
    private void signIn(String token) {
        Jwt jwt = jwtDecoder.decode(token);
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
    }

    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(_ -> work.get());
    }

    private static ServiceDetails details(String name) {
        return new ServiceDetails(name, null, null, BigDecimal.ONE, ServicePricing.FIXED, 5000, BigDecimal.valueOf(23),
                60, true, false);
    }
}
