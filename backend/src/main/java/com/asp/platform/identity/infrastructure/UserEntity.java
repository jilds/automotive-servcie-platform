package com.asp.platform.identity.infrastructure;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

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
        return new User(id, tenantId, name, email, passwordHash, role, active, createdAt);
    }
}
