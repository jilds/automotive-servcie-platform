package com.asp.platform.identity.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.identity.domain.ActivationInvalidException;
import com.asp.platform.identity.domain.ActivationToken;
import com.asp.platform.identity.domain.ActivationTokenRepository;
import com.asp.platform.identity.domain.Password;
import com.asp.platform.identity.domain.Tenant;
import com.asp.platform.identity.domain.TenantRepository;
import com.asp.platform.identity.domain.User;
import com.asp.platform.identity.domain.UserRepository;
import com.asp.platform.identity.domain.WorkshopSuspendedException;

/// Activation links: a new user (or a re-invited owner) sets their name and password and is signed in.
/// A link works once, for 72 hours, and only while the user is active and their workshop may sign in.
@Service
public class ActivationService {

    private final ActivationTokenRepository tokens;
    private final UserRepository users;
    private final TenantRepository tenants;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final Clock clock;
    private final SubscriptionPolicy subscriptions;

    ActivationService(ActivationTokenRepository tokens, UserRepository users, TenantRepository tenants,
            PasswordEncoder passwordEncoder, TokenIssuer tokenIssuer, Clock clock, SubscriptionPolicy subscriptions) {
        this.subscriptions = subscriptions;
        this.tokens = tokens;
        this.users = users;
        this.tenants = tenants;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.clock = clock;
    }

    /// @throws ActivationInvalidException                               unknown or used link, or inactive user
    /// @throws com.asp.platform.identity.domain.ActivationExpiredException link older than 72 hours
    /// @throws WorkshopSuspendedException                               the workshop is suspended, cancelled or expired
    @Transactional(readOnly = true)
    public ActivationPreview preview(String rawToken) {
        ActivationToken token = tokens.findByHash(ActivationToken.hash(rawToken))
                .orElseThrow(ActivationInvalidException::new);
        token.requireUsable(clock.instant());
        User user = activeUser(token);
        Tenant workshop = workshop(user);
        // Said before the form, not after the user typed a password that activation would then refuse.
        if (!subscriptions.allowsSignIn(workshop)) {
            throw new WorkshopSuspendedException();
        }
        return new ActivationPreview(user.email(), user.name(), workshop.shownName(), token.expiresAt());
    }

    /// Sets the user's name and password, uses up the link and signs the user in. The link's row is locked, so two
    /// requests with the same link cannot both succeed.
    @Transactional
    public Session activate(String rawToken, String name, String password) {
        Password.requireAcceptable(password);
        Instant now = clock.instant();
        ActivationToken token = tokens.lockByHash(ActivationToken.hash(rawToken))
                .orElseThrow(ActivationInvalidException::new);
        token.requireUsable(now);
        User user = activeUser(token);
        if (!subscriptions.allowsSignIn(workshop(user))) {
            throw new WorkshopSuspendedException();
        }
        User activated = users.save(user.activated(name, passwordEncoder.encode(password)));
        tokens.save(token.used(now));
        return new Session(tokenIssuer.issue(activated), Account.of(activated));
    }

    private User activeUser(ActivationToken token) {
        return users.findById(token.userId()).filter(User::active).orElseThrow(ActivationInvalidException::new);
    }

    private Tenant workshop(User user) {
        return tenants.findById(user.tenantId()).orElseThrow(ActivationInvalidException::new);
    }
}
