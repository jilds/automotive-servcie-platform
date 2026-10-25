package com.asp.platform.shared.persistence;

/// A JPA entity that mirrors one domain object, so [Upserts] can store the domain object in it.
///
/// @param <D> the domain type
public interface DomainEntity<D> {

    /// Copies the domain state onto this managed entity, children included, in place.
    void apply(D domain);

    D toDomain();
}
