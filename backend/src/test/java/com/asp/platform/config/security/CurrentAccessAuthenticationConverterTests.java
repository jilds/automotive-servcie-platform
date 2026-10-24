package com.asp.platform.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import com.asp.platform.identity.AccessDirectory;
import com.asp.platform.identity.TokenClaims;
import com.asp.platform.identity.UserAccess;
import com.asp.platform.shared.Role;

class CurrentAccessAuthenticationConverterTests {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private final Map<UUID, UserAccess> stored = new HashMap<>();
    private final Set<UUID> closedWorkshops = new HashSet<>();
    private final Set<UUID> admins = new HashSet<>();
    private final CurrentAccessAuthenticationConverter converter = new CurrentAccessAuthenticationConverter(
            new AccessDirectory() {
                @Override
                public Optional<UserAccess> access(UUID userId) {
                    return Optional.ofNullable(stored.get(userId));
                }

                @Override
                public boolean workshopOpen(UUID tenantId) {
                    return !closedWorkshops.contains(tenantId);
                }

                @Override
                public boolean isActivePlatformAdmin(UUID adminId) {
                    return admins.contains(adminId);
                }
            });

    @Test
    void should_take_the_role_from_the_database_when_the_token_claim_is_older() {
        stored.put(USER, new UserAccess(TENANT, Role.MECHANIC, true));

        AbstractAuthenticationToken auth = converter.convert(token(USER.toString(), TENANT.toString(), "OWNER"));

        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_MECHANIC");
        assertThat(auth.getName()).isEqualTo(USER.toString());
    }

    @Test
    void should_reject_the_token_when_the_user_is_inactive() {
        stored.put(USER, new UserAccess(TENANT, Role.OWNER, false));

        assertThatThrownBy(() -> converter.convert(token(USER.toString(), TENANT.toString(), "OWNER")))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void should_reject_the_token_when_the_user_does_not_exist() {
        assertThatThrownBy(() -> converter.convert(token(USER.toString(), TENANT.toString(), "OWNER")))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void should_reject_the_token_when_its_workshop_is_not_the_users() {
        stored.put(USER, new UserAccess(TENANT, Role.OWNER, true));

        assertThatThrownBy(() -> converter.convert(token(USER.toString(), UUID.randomUUID().toString(), "OWNER")))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void should_reject_the_token_when_the_subject_is_not_a_user_id() {
        assertThatThrownBy(() -> converter.convert(token("someone", TENANT.toString(), "OWNER")))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void should_reject_the_token_with_workshop_suspended_when_the_subscription_is_suspended() {
        stored.put(USER, new UserAccess(TENANT, Role.OWNER, true));
        closedWorkshops.add(TENANT);

        assertThatThrownBy(() -> converter.convert(token(USER.toString(), TENANT.toString(), "OWNER")))
                .isInstanceOf(WorkshopSuspendedAuthenticationException.class);
    }

    @Test
    void should_grant_only_the_platform_admin_role_to_an_active_platform_admin() {
        UUID admin = UUID.randomUUID();
        admins.add(admin);

        AbstractAuthenticationToken auth = converter.convert(adminToken(admin.toString()));

        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_PLATFORM_ADMIN");
    }

    @Test
    void should_reject_a_platform_admin_token_that_is_unknown_or_carries_a_workshop() {
        UUID admin = UUID.randomUUID();
        assertThatThrownBy(() -> converter.convert(adminToken(admin.toString())))
                .isInstanceOf(InvalidBearerTokenException.class);

        admins.add(admin);
        assertThatThrownBy(() -> converter.convert(token(admin.toString(), TENANT.toString(), "PLATFORM_ADMIN")))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    private static Jwt adminToken(String subject) {
        return Jwt.withTokenValue("t")
                .header("alg", "HS256")
                .subject(subject)
                .claim(TokenClaims.ROLE, TokenClaims.PLATFORM_ADMIN)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }

    private static Jwt token(String subject, String tenant, String role) {
        return Jwt.withTokenValue("t")
                .header("alg", "HS256")
                .subject(subject)
                .claim(TokenClaims.TENANT_ID, tenant)
                .claim(TokenClaims.ROLE, role)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }
}
