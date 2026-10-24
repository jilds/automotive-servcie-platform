package com.asp.platform.identity.infrastructure;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.jspecify.annotations.Nullable;

import com.asp.platform.identity.domain.User;
import com.asp.platform.shared.Role;

@Entity
@Table(name = "app_user")
class UserEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    /// Null until the user opens their activation link.
    @Column(name = "password_hash")
    private @Nullable String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /// Optimistic lock: Hibernate adds `where version = ?` to every update and raises it by one. Null until the
    /// entity is first saved, so Spring Data persists new entities instead of merging them.
    @Version
    @Column(nullable = false)
    private @Nullable Long version;

    @SuppressWarnings("NullAway.Init")
    protected UserEntity() {
    }

    UserEntity(User user) {
        this.id = user.id();
        this.tenantId = user.tenantId();
        this.createdAt = user.createdAt();
        apply(user);
    }

    void apply(User user) {
        this.name = user.name();
        this.email = user.email();
        this.passwordHash = user.passwordHash();
        this.role = user.role();
        this.active = user.active();
    }

    User toDomain() {
        return new User(id, tenantId, name, email, passwordHash, role, active, createdAt, version());
    }

    private long version() {
        Long v = version;
        return v == null ? 0 : v;
    }
}
