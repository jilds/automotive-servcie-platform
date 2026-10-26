package com.asp.platform.identity.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// The lead table is platform-level and not under row-level security: only the public site form writes it and only
/// the platform admin's endpoints read it.
public interface LeadRepository {

    Lead save(Lead lead);

    Optional<Lead> findById(UUID id);

    /// Newest first, at most `limit`, only `status` when given.
    List<Lead> list(Lead.@Nullable Status status, int limit);
}
