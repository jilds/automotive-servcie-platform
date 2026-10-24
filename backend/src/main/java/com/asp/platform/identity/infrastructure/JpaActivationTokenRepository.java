package com.asp.platform.identity.infrastructure;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import com.asp.platform.identity.domain.ActivationToken;
import com.asp.platform.identity.domain.ActivationTokenRepository;

@Repository
class JpaActivationTokenRepository implements ActivationTokenRepository {

    private final SpringDataActivationTokenRepository jpa;

    JpaActivationTokenRepository(SpringDataActivationTokenRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public ActivationToken save(ActivationToken token) {
        ActivationTokenEntity entity = jpa.findById(token.id())
                .map(existing -> {
                    existing.apply(token);
                    return existing;
                })
                .orElseGet(() -> jpa.save(new ActivationTokenEntity(token)));
        return entity.toDomain();
    }

    @Override
    public Optional<ActivationToken> findByHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash).map(ActivationTokenEntity::toDomain);
    }

    @Override
    public Optional<ActivationToken> lockByHash(String tokenHash) {
        return jpa.lockByTokenHash(tokenHash).map(ActivationTokenEntity::toDomain);
    }

    @Override
    public void deleteUnused(UUID userId) {
        jpa.deleteUnused(userId);
    }
}

interface SpringDataActivationTokenRepository extends JpaRepository<ActivationTokenEntity, UUID> {

    Optional<ActivationTokenEntity> findByTokenHash(String tokenHash);

    /// Waits at most 3 s for a concurrent activation with the same link; then 409 `try-again`.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select t from ActivationTokenEntity t where t.tokenHash = :tokenHash")
    Optional<ActivationTokenEntity> lockByTokenHash(String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ActivationTokenEntity t where t.userId = :userId and t.usedAt is null")
    void deleteUnused(UUID userId);
}
