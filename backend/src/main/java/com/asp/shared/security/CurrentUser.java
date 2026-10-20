package com.asp.shared.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reads the signed-in user's id and role from the JWT. */
public final class CurrentUser {

    private CurrentUser() {
    }

    private static Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new IllegalStateException("No authenticated user");
    }

    public static UUID id() {
        return UUID.fromString(jwt().getSubject());
    }

    public static Role role() {
        return Role.valueOf(jwt().getClaimAsString(JwtService.ROLE_CLAIM));
    }

    public static boolean canSeePrices() {
        return role() != Role.MECHANIC;
    }
}
