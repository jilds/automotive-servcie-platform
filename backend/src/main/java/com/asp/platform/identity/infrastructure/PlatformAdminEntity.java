package com.asp.platform.identity.infrastructure;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.asp.platform.identity.domain.PlatformAdmin;

@Entity
@Table(name = "platform_admin")
class PlatformAdminEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @SuppressWarnings("NullAway.Init")
    protected PlatformAdminEntity() {
    }

    PlatformAdminEntity(PlatformAdmin admin) {
        this.id = admin.id();
        this.createdAt = admin.createdAt();
        apply(admin);
    }

    void apply(PlatformAdmin admin) {
        this.name = admin.name();
        this.email = admin.email();
        this.passwordHash = admin.passwordHash();
        this.active = admin.active();
    }

    PlatformAdmin toDomain() {
        return new PlatformAdmin(id, name, email, passwordHash, active, createdAt);
    }
}
