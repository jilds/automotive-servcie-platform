package com.asp.platform.identity.application;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.asp.platform.identity.domain.ActivationToken;
import com.asp.platform.identity.domain.ActivationTokenRepository;
import com.asp.platform.identity.domain.User;
import com.asp.platform.shared.StatusTokens;

/// Issues activation links. Runs inside the caller's transaction.
@Component
class ActivationLinks {

    private final ActivationTokenRepository tokens;
    private final Clock clock;

    ActivationLinks(ActivationTokenRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    /// A new link for the user. Their older unused links stop working, so only the newest one handed out is valid.
    ActivationLink issue(User user) {
        String raw = StatusTokens.generate();
        tokens.deleteUnused(user.id());
        ActivationToken token = tokens.save(ActivationToken.issue(UUID.randomUUID(), user.id(), raw, clock.instant()));
        return new ActivationLink(raw, token.expiresAt());
    }
}
