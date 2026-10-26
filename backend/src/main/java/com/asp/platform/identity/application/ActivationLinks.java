package com.asp.platform.identity.application;

import java.time.Clock;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.identity.ActivationLinkIssued;
import com.asp.platform.identity.domain.ActivationToken;
import com.asp.platform.identity.domain.ActivationTokenRepository;
import com.asp.platform.identity.domain.User;
import com.asp.platform.shared.StatusTokens;

/// Issues activation links. Runs inside the caller's transaction, and publishes [ActivationLinkIssued] so the link
/// can be mailed once that transaction commits.
@Component
class ActivationLinks {

    private final ActivationTokenRepository tokens;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ActivationLinks(ActivationTokenRepository tokens, ApplicationEventPublisher events, Clock clock) {
        this.tokens = tokens;
        this.events = events;
        this.clock = clock;
    }

    /// A new link for the user. Their older unused links stop working, so only the newest one handed out is valid.
    ///
    /// @param firstOwner true for the first owner of a workshop the platform admin registered
    // MANDATORY: the mail listener runs after the caller's commit, so a call outside a transaction would mail nothing.
    @Transactional(propagation = Propagation.MANDATORY)
    ActivationLink issue(User user, boolean firstOwner) {
        String raw = StatusTokens.generate();
        tokens.deleteUnused(user.id());
        ActivationToken token = tokens.save(ActivationToken.issue(UUID.randomUUID(), user.id(), raw, clock.instant()));
        ActivationLink link = new ActivationLink(raw, token.expiresAt());
        events.publishEvent(new ActivationLinkIssued(user.tenantId(), user.email(), user.name(), link.path(),
                link.expiresAt(), firstOwner));
        return link;
    }
}
