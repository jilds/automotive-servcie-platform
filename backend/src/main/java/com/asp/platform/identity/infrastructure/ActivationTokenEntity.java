package com.asp.platform.identity.infrastructure;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import com.asp.platform.identity.domain.ActivationToken;

@Entity
@Table(name = "activation_token")
class ActivationTokenEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private @Nullable Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @SuppressWarnings("NullAway.Init")
    protected ActivationTokenEntity() {
    }

    ActivationTokenEntity(ActivationToken token) {
        this.id = token.id();
        this.userId = token.userId();
        this.tokenHash = token.tokenHash();
        this.expiresAt = token.expiresAt();
        this.createdAt = token.createdAt();
        this.usedAt = token.usedAt();
    }

    void apply(ActivationToken token) {
        this.usedAt = token.usedAt();
    }

    ActivationToken toDomain() {
        return new ActivationToken(id, userId, tokenHash, expiresAt, usedAt, createdAt);
    }
}
