package com.asp.platform.config.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.asp.platform.identity.AccessDirectory;
import com.asp.platform.identity.TokenClaims;
import com.asp.platform.identity.UserAccess;

/// Turns a verified token into an authentication using the user's **current** access, not the role and
/// state written into the token when it was issued. A token of a deactivated or unknown user, or one whose
/// workshop no longer matches, is rejected with 401; a changed role applies to the next request.
class CurrentAccessAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    static final String ROLE_PREFIX = "ROLE_";

    private final AccessDirectory directory;

    CurrentAccessAuthenticationConverter(AccessDirectory directory) {
        this.directory = directory;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId = uuid(jwt.getSubject());
        UUID tokenTenant = uuid(jwt.getClaimAsString(TokenClaims.TENANT_ID));
        UserAccess access = Optional.ofNullable(userId).flatMap(directory::access)
                .filter(UserAccess::active)
                .filter(current -> current.tenantId().equals(tokenTenant))
                .orElseThrow(() -> new InvalidBearerTokenException("The user may no longer sign in"));
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(ROLE_PREFIX + access.role())),
                jwt.getSubject());
    }

    private static @Nullable UUID uuid(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
