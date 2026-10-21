package com.asp.platform.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import com.asp.platform.identity.TokenClaims;
import com.asp.platform.identity.UserAccess;
import com.asp.platform.shared.Role;

class CurrentAccessAuthenticationConverterTests {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private final Map<UUID, UserAccess> stored = new HashMap<>();
    private final CurrentAccessAuthenticationConverter converter =
            new CurrentAccessAuthenticationConverter(id -> Optional.ofNullable(stored.get(id)));

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
