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

/// Turns a verified token into an authentication using the account's **current** access, not the role and
/// state written into the token when it was issued:
/// - a workshop user's token is rejected with 401 when the user is deactivated or unknown, when their workshop
///   no longer matches, or (code `workshop-suspended`) when the workshop's subscription is suspended or cancelled;
///   a changed role applies to the next request;
/// - a platform admin's token (role claim `PLATFORM_ADMIN`, no tenant) is rejected when the admin is inactive or
///   unknown, and only ever grants `ROLE_PLATFORM_ADMIN`.
class CurrentAccessAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    static final String ROLE_PREFIX = "ROLE_";
    static final String PLATFORM_ADMIN_AUTHORITY = ROLE_PREFIX + TokenClaims.PLATFORM_ADMIN;

    private final AccessDirectory directory;

    CurrentAccessAuthenticationConverter(AccessDirectory directory) {
        this.directory = directory;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID subject = uuid(jwt.getSubject());
        if (TokenClaims.PLATFORM_ADMIN.equals(jwt.getClaimAsString(TokenClaims.ROLE))) {
            if (subject == null || jwt.hasClaim(TokenClaims.TENANT_ID) || !directory.isActivePlatformAdmin(subject)) {
                throw new InvalidBearerTokenException("The account may no longer sign in");
            }
            return authenticated(jwt, PLATFORM_ADMIN_AUTHORITY);
        }
        UUID tokenTenant = uuid(jwt.getClaimAsString(TokenClaims.TENANT_ID));
        UserAccess access = Optional.ofNullable(subject).flatMap(directory::access)
                .filter(UserAccess::active)
                .filter(current -> current.tenantId().equals(tokenTenant))
                .orElseThrow(() -> new InvalidBearerTokenException("The user may no longer sign in"));
        if (!directory.workshopOpen(access.tenantId())) {
            throw new WorkshopSuspendedAuthenticationException();
        }
        return authenticated(jwt, ROLE_PREFIX + access.role());
    }

    private static JwtAuthenticationToken authenticated(Jwt jwt, String authority) {
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(authority)), jwt.getSubject());
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
