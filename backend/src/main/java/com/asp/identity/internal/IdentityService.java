package com.asp.identity.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.shared.security.JwtService;
import com.asp.shared.security.Role;
import com.asp.shared.web.ConflictException;

@Service
class IdentityService {

    private final TenantRepository tenants;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;

    IdentityService(TenantRepository tenants, AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwt) {
        this.tenants = tenants;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
    }

    @Transactional
    AuthDtos.TokenResponse signup(AuthDtos.SignupRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw new ConflictException("Email already registered");
        }
        Tenant tenant = tenants.save(new Tenant(req.workshopName(), req.nif(), "PT", "Europe/Lisbon"));
        AppUser owner = users.save(new AppUser(tenant.getId(), req.ownerName(), req.email().toLowerCase(),
                passwordEncoder.encode(req.password()), Role.OWNER));
        return token(owner);
    }

    @Transactional(readOnly = true)
    AuthDtos.TokenResponse login(AuthDtos.LoginRequest req) {
        AppUser user = users.findByEmailIgnoreCase(req.email())
                .filter(AppUser::isActive)
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        return token(user);
    }

    @Transactional
    AuthDtos.UserResponse invite(UUID tenantId, AuthDtos.InviteRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw new ConflictException("Email already registered");
        }
        AppUser user = users.save(new AppUser(tenantId, req.name(), req.email().toLowerCase(),
                passwordEncoder.encode(req.temporaryPassword()), req.role()));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    List<AuthDtos.UserResponse> list(UUID tenantId) {
        return users.findByTenantIdOrderByName(tenantId).stream().map(IdentityService::toResponse).toList();
    }

    private AuthDtos.TokenResponse token(AppUser user) {
        return new AuthDtos.TokenResponse(jwt.issue(user.getId(), user.getTenantId(), user.getRole(), user.getEmail()),
                "Bearer", jwt.ttlSeconds(), toResponse(user));
    }

    private static AuthDtos.UserResponse toResponse(AppUser u) {
        return new AuthDtos.UserResponse(u.getId(), u.getTenantId(), u.getName(), u.getEmail(), u.getRole());
    }
}
