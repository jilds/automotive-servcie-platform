package com.asp.platform.shared.persistence;

import java.util.function.Function;

import org.springframework.data.jpa.repository.JpaRepository;

/// The insert-or-update of an aggregate: the stored entity takes the domain state, or a new entity is saved. Runs
/// inside the caller's transaction (the application layer starts it).
public final class Upserts {

    private Upserts() {
    }

    /// Updates the stored entity or saves a new one, then flushes, so the returned domain object carries the stored
    /// `version` and a constraint violation surfaces here instead of at commit.
    public static <D, E extends DomainEntity<D>, ID> D upsert(JpaRepository<E, ID> jpa, ID id, D domain,
            Function<? super D, ? extends E> newEntity) {
        return upsert(jpa, id, domain, newEntity, true);
    }

    /// Same as [#upsert] without the flush: the SQL runs at the next flush or at commit.
    public static <D, E extends DomainEntity<D>, ID> D upsertWithoutFlush(JpaRepository<E, ID> jpa, ID id, D domain,
            Function<? super D, ? extends E> newEntity) {
        return upsert(jpa, id, domain, newEntity, false);
    }

    /// A new entity is read back from what `save` returns: an entity without `@Version` is merged, not persisted,
    /// so the managed copy is the returned one, not the instance passed in.
    private static <D, E extends DomainEntity<D>, ID> D upsert(JpaRepository<E, ID> jpa, ID id, D domain,
            Function<? super D, ? extends E> newEntity, boolean flush) {
        E entity = jpa.findById(id)
                .map(existing -> {
                    existing.apply(domain);
                    return existing;
                })
                .orElseGet(() -> jpa.save(newEntity.apply(domain)));
        if (flush) {
            jpa.flush();
        }
        return entity.toDomain();
    }
}
