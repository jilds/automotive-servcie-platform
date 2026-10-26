package com.asp.platform.identity.domain;

import java.util.Optional;
import java.util.UUID;

/// Platform admins. A separate table from workshop users, see ADR 0003.
public interface PlatformAdminRepository {

    PlatformAdmin save(PlatformAdmin admin);

    Optional<PlatformAdmin> findById(UUID id);

    Optional<PlatformAdmin> findByEmail(String normalizedEmail);

    boolean existsByEmail(String normalizedEmail);

    boolean existsAny();

    /// The e-mail addresses of the active platform admins.
    java.util.List<String> activeEmails();
}
