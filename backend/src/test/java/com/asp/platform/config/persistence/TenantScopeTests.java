package com.asp.platform.config.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.asp.platform.identity.TokenClaims;
import com.asp.platform.shared.TenantScope;

/// The workshop a connection is bound to (ADR 0004): a signed-in user's token, else the [TenantScope], else none.
class TenantScopeTests {

    private static final UUID TOKEN_TENANT = UUID.randomUUID();
    private static final UUID SCOPE_TENANT = UUID.randomUUID();

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_bind_nothing_without_a_token_or_a_scope() {
        assertThat(TenantScope.current()).isEmpty();
        assertThat(TenantAwareDataSource.currentTenant()).isEmpty();
    }

    @Test
    void should_bind_the_scope_without_a_token() {
        TenantScope.runAs(SCOPE_TENANT, () -> {
            assertThat(TenantScope.current()).contains(SCOPE_TENANT);
            assertThat(TenantAwareDataSource.currentTenant()).isEqualTo(SCOPE_TENANT.toString());
        });
        assertThat(TenantScope.current()).isEmpty();
    }

    @Test
    void should_let_the_token_win_over_the_scope() {
        signIn(jwt(TOKEN_TENANT.toString()));

        TenantScope.runAs(SCOPE_TENANT,
                () -> assertThat(TenantAwareDataSource.currentTenant()).isEqualTo(TOKEN_TENANT.toString()));
    }

    @Test
    void should_never_fall_back_to_the_scope_for_a_token_without_a_workshop() {
        // A platform admin's token has no tenant_id: still the token decides, so no workshop rows.
        signIn(jwt(null));

        TenantScope.runAs(SCOPE_TENANT, () -> assertThat(TenantAwareDataSource.currentTenant()).isEmpty());
    }

    @Test
    void should_restore_the_outer_workshop_after_a_nested_scope() {
        UUID inner = UUID.randomUUID();
        TenantScope.runAs(SCOPE_TENANT, () -> {
            TenantScope.runAs(inner, () -> assertThat(TenantScope.current()).contains(inner));
            assertThat(TenantScope.current()).contains(SCOPE_TENANT);
        });
        assertThat(TenantScope.current()).isEmpty();
    }

    @Test
    void should_return_results_and_pass_checked_exceptions_through() throws IOException {
        assertThat(TenantScope.callAs(SCOPE_TENANT, () -> TenantScope.current().orElseThrow()))
                .isEqualTo(SCOPE_TENANT);

        AtomicReference<IOException> thrown = new AtomicReference<>();
        try {
            TenantScope.<Void, IOException>callAs(SCOPE_TENANT, () -> {
                throw new IOException("boom");
            });
        } catch (IOException e) {
            thrown.set(e);
        }
        assertThat(thrown.get()).hasMessage("boom");
        assertThat(TenantScope.current()).isEmpty();
    }

    @Test
    void should_not_reach_other_threads() throws InterruptedException {
        AtomicReference<Boolean> seen = new AtomicReference<>();
        TenantScope.runAs(SCOPE_TENANT, () -> {
            Thread other = Thread.ofVirtual().unstarted(() -> seen.set(TenantScope.current().isPresent()));
            other.start();
            try {
                other.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(seen.get()).isFalse();
    }

    @Test
    void should_refuse_to_switch_the_workshop_inside_a_transaction() {
        // The transaction already took its connection, bound to the outer workshop: a scope opened now would still
        // read and write that workshop's rows.
        TransactionTemplate transaction = new TransactionTemplate(new NoOpTransactionManager());

        transaction.executeWithoutResult(_ -> {
            assertThatIllegalStateException().isThrownBy(() -> TenantScope.runAs(SCOPE_TENANT, () -> { }))
                    .withMessageContaining("outside a transaction");
            assertThatIllegalStateException().isThrownBy(() -> TenantScope.callAs(SCOPE_TENANT, () -> 1));
        });
        TenantScope.runAs(SCOPE_TENANT, () -> transaction.executeWithoutResult(
                _ -> assertThat(TenantScope.current()).contains(SCOPE_TENANT)));
    }

    private static void signIn(Jwt jwt) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    private static Jwt jwt(String tenantId) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "HS256").subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (tenantId != null) {
            builder.claim(TokenClaims.TENANT_ID, tenantId);
        } else {
            builder.claim(TokenClaims.ROLE, TokenClaims.PLATFORM_ADMIN);
        }
        return builder.build();
    }

    /// Marks a transaction active the way every Spring transaction manager does, without a database.
    private static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
