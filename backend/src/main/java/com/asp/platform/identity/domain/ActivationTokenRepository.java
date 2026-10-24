package com.asp.platform.identity.domain;

import java.util.Optional;
import java.util.UUID;

/// Activation links, on the `app_user` side: not under row-level security, found only by the hash of a token the
/// caller holds or by the user they belong to.
public interface ActivationTokenRepository {

    ActivationToken save(ActivationToken token);

    Optional<ActivationToken> findByHash(String tokenHash);

    /// Same as [#findByHash] but locks the row until the transaction ends, so a link cannot be used twice at once.
    Optional<ActivationToken> lockByHash(String tokenHash);

    /// Removes the user's links that were never used, so only the newest one works.
    void deleteUnused(UUID userId);
}
