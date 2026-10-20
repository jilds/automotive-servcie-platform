package com.asp.platform.identity.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.identity.domain.EmailAlreadyRegisteredException;
import com.asp.platform.identity.domain.InvalidCredentialsException;
import com.asp.platform.identity.domain.Tenant;
import com.asp.platform.identity.domain.TenantRepository;
import com.asp.platform.identity.domain.User;
import com.asp.platform.identity.domain.UserRepository;
import com.asp.platform.shared.Role;

@Service
public class IdentityService {

    private final TenantRepository tenants;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final Clock clock;

    IdentityService(TenantRepository tenants, UserRepository users, PasswordEncoder passwordEncoder,
            TokenIssuer tokenIssuer, Clock clock) {
        this.tenants = tenants;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.clock = clock;
    }

    /// Registers a new workshop and makes the person signing up its owner.
    @Transactional
    public Session signup(SignupCommand command) {
        requireUnusedEmail(command.email());
        Tenant tenant = tenants.save(Tenant.register(UUID.randomUUID(), command.workshopName(), command.nif(),
                clock.instant()));
        User owner = users.save(User.create(UUID.randomUUID(), tenant.id(), command.ownerName(), command.email(),
                passwordEncoder.encode(command.password()), Role.OWNER, clock.instant()));
        return new Session(tokenIssuer.issue(owner), owner);
    }

    @Transactional(readOnly = true)
    public Session login(String email, String password) {
        User user = users.findByEmail(User.normalizeEmail(email))
                .filter(User::active)
                .filter(candidate -> passwordEncoder.matches(password, candidate.passwordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        return new Session(tokenIssuer.issue(user), user);
    }

    @Transactional
    public User invite(UUID tenantId, InviteUserCommand command) {
        requireUnusedEmail(command.email());
        return users.save(User.create(UUID.randomUUID(), tenantId, command.name(), command.email(),
                passwordEncoder.encode(command.temporaryPassword()), command.role(), clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<User> usersOf(UUID tenantId) {
        return users.findByTenant(tenantId);
    }

    private void requireUnusedEmail(String email) {
        if (users.existsByEmail(User.normalizeEmail(email))) {
            throw new EmailAlreadyRegisteredException();
        }
    }
}
