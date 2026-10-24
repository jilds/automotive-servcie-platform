package com.asp.platform.identity.infrastructure;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.asp.platform.identity.domain.Logo;

@Entity
@Table(name = "tenant_logo")
class TenantLogoEntity {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] content;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @SuppressWarnings("NullAway.Init")
    protected TenantLogoEntity() {
    }

    TenantLogoEntity(UUID tenantId, Logo logo) {
        this.tenantId = tenantId;
        apply(logo);
    }

    void apply(Logo logo) {
        this.content = logo.content();
        this.contentType = logo.contentType();
        this.sha256 = logo.sha256();
        this.updatedAt = logo.updatedAt();
    }

    Logo toDomain() {
        return Logo.stored(content, contentType, sha256, updatedAt);
    }
}
