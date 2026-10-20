package com.asp.platform.identity.infrastructure;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import com.asp.platform.identity.domain.Tenant;

@Entity
@Table(name = "tenant")
class TenantEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    private @Nullable String nif;

    @Column(nullable = false, length = 2)
    private String country;

    @Column(nullable = false)
    private String timezone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @SuppressWarnings("NullAway.Init")
    protected TenantEntity() {
    }

    TenantEntity(Tenant tenant) {
        this.id = tenant.id();
        this.name = tenant.name();
        this.nif = tenant.nif();
        this.country = tenant.country();
        this.timezone = tenant.timezone();
        this.createdAt = tenant.createdAt();
    }

    Tenant toDomain() {
        return new Tenant(id, name, nif, country, timezone, createdAt);
    }
}
