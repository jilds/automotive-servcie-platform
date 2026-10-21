package com.asp.platform.identity.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import com.asp.platform.identity.domain.Tenant;
import com.asp.platform.identity.domain.WorkshopSettings;

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

    @Column(name = "default_vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal defaultVatRate;

    @Column(name = "labour_rate_cents", nullable = false)
    private long labourRateCents;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @SuppressWarnings("NullAway.Init")
    protected TenantEntity() {
    }

    TenantEntity(Tenant tenant) {
        this.id = tenant.id();
        this.createdAt = tenant.createdAt();
        apply(tenant);
    }

    void apply(Tenant tenant) {
        WorkshopSettings s = tenant.settings();
        this.name = s.name();
        this.nif = s.nif();
        this.country = s.country();
        this.timezone = s.timezone();
        this.defaultVatRate = s.defaultVatRate();
        this.labourRateCents = s.labourRateCents();
    }

    Tenant toDomain() {
        return new Tenant(id,
                new WorkshopSettings(name, nif, country, timezone, defaultVatRate, labourRateCents), createdAt);
    }
}
