package com.asp.shared.tenancy;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Resolves the workshop (tenant) the current request acts for.
 * The tenant comes from the signed JWT; code that runs before login (signup) can bind one explicitly.
 */
public final class TenantContext {

    public static final String TENANT_CLAIM = "tenant_id";

    private static final ThreadLocal<UUID> BOUND = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Optional<UUID> current() {
        UUID bound = BOUND.get();
        if (bound != null) {
            return Optional.of(bound);
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String claim = jwt.getClaimAsString(TENANT_CLAIM);
            if (claim != null) {
                return Optional.of(UUID.fromString(claim));
            }
        }
        return Optional.empty();
    }

    public static UUID require() {
        return current().orElseThrow(() -> new IllegalStateException("No tenant bound to this request"));
    }

    /** Runs the action with the given tenant bound, for flows that happen before a JWT exists. */
    public static <T> T callAs(UUID tenantId, Supplier<T> action) {
        UUID previous = BOUND.get();
        BOUND.set(tenantId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                BOUND.remove();
            } else {
                BOUND.set(previous);
            }
        }
    }
}
