package com.asp.platform.identity.application;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.asp.platform.identity.domain.ActivationToken;
import com.asp.platform.identity.domain.ActivationTokenRepository;
import com.asp.platform.identity.domain.User;

/// Issues activation links. Runs inside the caller's transaction.
@Component
class ActivationLinks {

    private static final int TOKEN_BYTES = 32;

    private final ActivationTokenRepository tokens;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    ActivationLinks(ActivationTokenRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    /// A new link for the user. Their older unused links stop working, so only the newest one handed out is valid.
    ActivationLink issue(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.deleteUnused(user.id());
        ActivationToken token = tokens.save(ActivationToken.issue(UUID.randomUUID(), user.id(), raw, clock.instant()));
        return new ActivationLink(raw, token.expiresAt());
    }
}
