package com.asp.platform.identity.application;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.identity.domain.AlreadyActivatedException;
import com.asp.platform.identity.domain.EmailAlreadyRegisteredException;
import com.asp.platform.identity.domain.InvalidCredentialsException;
import com.asp.platform.identity.domain.PlatformAdmin;
import com.asp.platform.identity.domain.PlatformAdminRepository;
import com.asp.platform.identity.domain.Team;
import com.asp.platform.identity.domain.Tenant;
import com.asp.platform.identity.domain.TenantRepository;
import com.asp.platform.identity.domain.User;
import com.asp.platform.identity.domain.UserNotFoundException;
import com.asp.platform.identity.domain.UserRepository;
import com.asp.platform.identity.domain.WorkshopSuspendedException;
import com.asp.platform.shared.Role;

/// Login for workshop users and platform admins, and the owner's team management. Workshops are created by the
/// platform admin ([WorkshopAdminService]); there is no public signup.
@Service
public class IdentityService {

    private final TenantRepository tenants;
    private final UserRepository users;
    private final PlatformAdminRepository admins;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final AccessDirectoryService access;
    private final ActivationLinks links;
    private final Clock clock;
    private final SubscriptionPolicy subscriptions;

    IdentityService(TenantRepository tenants, UserRepository users, PlatformAdminRepository admins,
            PasswordEncoder passwordEncoder, TokenIssuer tokenIssuer, AccessDirectoryService access,
            ActivationLinks links, Clock clock, SubscriptionPolicy subscriptions) {
        this.subscriptions = subscriptions;
        this.tenants = tenants;
        this.users = users;
        this.admins = admins;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.access = access;
        this.links = links;
        this.clock = clock;
    }

    /// One login for everyone: a workshop user, else a platform admin with that email.
    ///
    /// @throws InvalidCredentialsException when the email or password is wrong, the account is inactive or the
    ///                                     user has not activated their account yet
    /// @throws WorkshopSuspendedException  when the password is right but the workshop's subscription is suspended
    ///                                     or cancelled
    @Transactional(readOnly = true)
    public Session login(String email, String password) {
        String normalized = User.normalizeEmail(email);
        Optional<User> user = users.findByEmail(normalized)
                .filter(User::active)
                .filter(candidate -> matches(password, candidate.passwordHash()));
        if (user.isPresent()) {
            if (!tenants.findById(user.get().tenantId()).map(subscriptions::allowsSignIn).orElse(false)) {
                throw new WorkshopSuspendedException();
            }
            return new Session(tokenIssuer.issue(user.get()), Account.of(user.get()));
        }
        PlatformAdmin admin = admins.findByEmail(normalized)
                .filter(PlatformAdmin::active)
                .filter(candidate -> matches(password, candidate.passwordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        return new Session(tokenIssuer.issue(admin), Account.of(admin));
    }

    /// Adds a team member without a password and returns their activation link.
    @Transactional
    public Invitation invite(UUID tenantId, InviteUserCommand command) {
        requireUnusedEmail(command.email());
        User user = users.save(User.invite(UUID.randomUUID(), tenantId, command.name(), command.email(),
                command.role(), clock.instant()));
        return new Invitation(user, links.issue(user, false));
    }

    /// A new activation link for a team member who has not activated yet; their older link stops working.
    @Transactional
    public Invitation reissueInvitation(UUID tenantId, UUID userId) {
        User user = users.findById(userId)
                .filter(candidate -> candidate.tenantId().equals(tenantId))
                .orElseThrow(UserNotFoundException::new);
        if (!user.pending()) {
            throw new AlreadyActivatedException();
        }
        return new Invitation(user, links.issue(user, false));
    }

    @Transactional(readOnly = true)
    public List<User> usersOf(UUID tenantId) {
        return users.findByTenant(tenantId);
    }

    /// Changes a team member's role and whether they may sign in. A deactivated user can no longer log in,
    /// and tokens already issued to them stop working on their next request; a new role applies to it too.
    /// The team's rows are locked first, so the "at least one active owner" rule holds under concurrency.
    ///
    /// @param expectedVersion the member's version the owner read
    /// @throws com.asp.platform.shared.StaleDataException when someone else changed the member since
    @Transactional
    public User changeAccess(UUID tenantId, UUID actorId, UUID userId, Role role, boolean active,
            long expectedVersion) {
        Team team = new Team(users.lockTeam(tenantId));
        User changed = team.changeAccess(actorId, userId, role, active);
        changed.requireVersion(expectedVersion);
        changed = users.save(changed);
        access.evict(changed.id());
        return changed;
    }

    /// Emails are unique across workshop users and platform admins, because one login serves both.
    void requireUnusedEmail(String email) {
        String normalized = User.normalizeEmail(email);
        if (users.existsByEmail(normalized) || admins.existsByEmail(normalized)) {
            throw new EmailAlreadyRegisteredException();
        }
    }

    private boolean matches(String password, @Nullable String hash) {
        return hash != null && passwordEncoder.matches(password, hash);
    }
}
